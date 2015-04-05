(ns ceres-geschichte.core
  (:require [hasch.core :refer [uuid]]
            [konserve.store :refer [new-mem-store]]
            [konserve.filestore :refer [new-fs-store]]
            [gezwitscher.core :refer [start-filter-stream gezwitscher]]
            [konserve.protocols :refer [-get-in -assoc-in -update-in]]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.stage :as s]
            [clj-time.core :as t]
            [geschichte.p2p.fetch :refer [fetch]]
            [geschichte.p2p.hash :refer [ensure-hash]]
            [geschichte.realize :refer [commit-value]]
            [geschichte.p2p.block-detector :refer [block-detector]]
            [geschichte.platform :refer [create-http-kit-handler! <!? start stop]]
            [clojure.core.async :refer [>!!]]
            [aprint.core :refer [aprint]]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn setup-logging! [dir]
  (def log-store (<!? (new-fs-store (str dir "/" (java.util.Date.)))))
  (def log-counter (atom 0))

  (timbre/set-config! [:appenders :standard-out :min-level] :debug)

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



(defn setup-benchmark! [dir]
  (<!? (new-fs-store dir)))

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

(defn init [{:keys [user socket repo-name fs-store]}]
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
    (<!? (s/transact stage [user r-id "master"] [[(find-fn 'init) "mem"]]))
    (<!? (s/commit! stage {user {r-id #{"master"}}})) ;; master branch
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
                   (update-in [:log-db] init-log-db)
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

  (def store (<!? (new-mem-store)))

  (def peer (client-peer "archimedes" store (comp (partial fetch store) ensure-hash)))

  (def stage (<!? (s/create-stage! "kordano@topiq.es" peer eval)))

  (<!? (s/connect! stage "ws://127.0.0.1:31744"))


  (def user "kordano@topiq.es")

  (def repo #uuid "ac87a592-1d10-471e-87d3-2f3ad9518129")


  (<!? (s/subscribe-repos! stage {user {repo #{"master"}}}))


  )
