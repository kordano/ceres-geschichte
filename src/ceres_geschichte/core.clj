(ns ceres-geschichte.core
  (:gen-class :main true)
  (:require [hasch.core :refer [uuid]]
            [konserve.store :refer [new-mem-store]]
            [konserve.filestore :refer [new-fs-store]]
            [gezwitscher.core :refer [start-filter-stream gezwitscher]]
            [konserve.protocols :refer [-get-in -assoc-in -update-in -bassoc]]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.stage :as s]
            [taoensso.nippy :as nippy]
            [geschichte.p2p.fetch :refer [fetch]]
            [geschichte.p2p.hash :refer [ensure-hash]]
            [geschichte.realize :refer [commit-value]]
            [geschichte.p2p.block-detector :refer [block-detector]]
            [geschichte.platform :refer [create-http-kit-handler! <!? start stop]]
            [clojure.core.async :refer [>!!]]
            [aprint.core :refer [aprint]]
            [taoensso.timbre :as timbre])
  (:import [java.io DataOutputStream ByteArrayOutputStream]))

(timbre/refer-timbre)

(defn setup-logging! [dir]
  (def log-store (<!? (new-fs-store (str dir "/" (java.util.Date.)))))
  (def log-counter (atom 0))

  (timbre/set-config! [:appenders :standard-out :min-level] :error)

  (timbre/set-config! [:appenders :fs-store] {:doc "Simple file appender."
                                              :min-level nil :enabled? true
                                              :fn (fn [{:keys [ap-config] :as args}]
                                                    (with-open [baos (ByteArrayOutputStream.)
                                                                dos (DataOutputStream. baos)]
                                                      (try
                                                        (nippy/freeze-to-stream! dos (map (fn [a] (if (nippy/freezable? a) a (str a)))
                                                                                          args))
                                                        (<!? (-bassoc log-store (swap! log-counter inc) (.toByteArray baos)))
                                                        (catch Exception e
                                                          (.printStacktrace e)))))}))



(defn stop-peer [state]
  (stop (get-in @state [:geschichte :peer])))

(def eval-map
  {'(fn init [_ name]
      {:store name
       :data []})
   (fn [_ name]
     {:store name
      :data []})
   '(fn transact-entry [old entry]
      (update-in old [:data] concat entry))
   (fn [old entry]
     (update-in old [:data] concat entry))})


(defn mapped-eval [code]
  (if (eval-map code)
    (eval-map code)
    (do (debug "eval-map didn't match:" code)
        (fn [old _] old))))

(defn find-fn [name]
  (first (filter (fn [[_ fn-name]]
                   (= name fn-name))
                 (keys eval-map))))

(defn init [{:keys [user socket repo-name fs-store init?]}]
  (let [user (or user "kordano@topiq.es")
        socket (or socket "ws://127.0.0.1:31744")
        store (<!? (if fs-store
                     (new-fs-store fs-store)
                     (new-mem-store)))
        peer-server (server-peer (create-http-kit-handler! socket)
                                 store
                                 (comp (partial block-detector :peer-core)
                                       (partial fetch store)
                                       ensure-hash
                                       (partial block-detector :p2p-surface)))
        _ (start peer-server)
        stage (<!? (s/create-stage! user peer-server eval))
        _ (<!? (s/connect! stage socket))
        r-id (<!? (s/create-repo! stage (or repo-name "tweets collection")))]
    (when init?
      (<!? (s/transact stage [user r-id "master"] [[(find-fn 'init) "mem"]]))
      (<!? (s/commit! stage {user {r-id #{"master"}}}))) ;; master branch
    {:store store
     :peer peer-server
     :stage stage
     :repo r-id
     :user user}))


(defn transact-status
  "Transact incoming status to geschichte and commit"
  [state status]
  (let [{:keys [store peer stage repo user]} (get-in @state [:geschichte])
        k-benchmark (get-in @state [:k-benchmark])]
    (<!? (s/transact stage [user repo "master"] [[(find-fn 'transact-entry) [status]]]))
    (let [pre-time (System/currentTimeMillis)]
      (<!? (s/commit! stage {user {repo #{"master"}}}))
      (<!? (-update-in k-benchmark [:commit-delays] #(conj % {:duration (- (System/currentTimeMillis) pre-time)}))))
   state))


(defn get-current-state
  "Realize head of current geschichte master branch"
  [state]
  (let [{:keys [store peer stage repo user]} (get-in @state [:geschichte])
        causal-order (get-in @stage [user repo :state :causal-order])
        master-head (first (get-in @stage [user repo :state :branches "master"]))]
    (<!? (commit-value store mapped-eval causal-order master-head))))


(defn initialize-state
  "Initialize the server state using a given config file"
  [path]
  (let [config (-> path slurp read-string
                   (update-in [:k-benchmark] #(<!? (new-fs-store %)))
                   (update-in [:geschichte] init))]
    (debug "STATE:" config)
    (atom config)))


(defn -main [config-path & args]
  (info "warming up...")
  (let [state (initialize-state config-path)]
    (setup-logging! (:k-log @state))
    (let [{{:keys [follow track credentials]} :twitter} @state]
      (start-filter-stream follow track (fn [status] (transact-status state status)) credentials))
    (info "server started!")))


(comment

  (def state (initialize-state "resources/test-config.edn"))

  (let [{{:keys [follow track credentials]} :app} @state]
      (start-filter-stream follow track (fn [status] (transact-status state status)) credentials))

  (def user "kordano@topiq.es")

  (def repo #uuid "5fee7572-8083-453e-8e51-0f08087cefac")

  ;; client 1
  (def store (<!? (new-mem-store)))

  (def peer (client-peer "archimedes" store (comp (partial fetch store) ensure-hash)))

  (def stage (<!? (s/create-stage! "kordano@topiq.es" peer eval)))

  (<!? (s/connect! stage "ws://deimos:31744"))


  (<!? (s/subscribe-repos! stage {user {repo #{"master"}}}))

  ;; client 2
  (def store-2 (<!? (new-mem-store)))

  (def peer-2 (client-peer "archimedes" store-2 (comp (partial fetch store-2) ensure-hash)))

  (def stage-2 (<!? (s/create-stage! "kordano@topiq.es" peer-2 eval)))

  (<!? (s/connect! stage-2 "ws://172.17.0.12:31744"))

  (<!? (s/subscribe-repos! stage-2 {user {repo #{"master"}}}))






  (def b-store (<!? (new-fs-store "/opt/data/ceres-geschichte/k-benchmark")))


  (let [durations (map :duration (<!? (-get-in b-store [:commit-delays])))]
    [(count durations)])

  (timbre/set-config! [:appenders :standard-out :min-level] :error)

  )
