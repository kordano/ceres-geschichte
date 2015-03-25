FROM ubuntu:latest

# Update the APT cache
RUN apt-get   update

# grab little helpers
RUN apt-get install -y curl git wget unzip

# grab java
RUN apt-get install -y software-properties-common
RUN apt-get -y install openjdk-7-jre-headless
RUN apt-get install -y mongodb-clients
ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64

# fix wget
RUN export HTTP_CLIENT="wget --no-check-certificate -O"

# grab leiningen
RUN wget https://raw.github.com/technomancy/leiningen/stable/bin/lein -O /usr/local/bin/lein
RUN chmod +x /usr/local/bin/lein
ENV LEIN_ROOT yes
RUN lein

# grab scripts
ADD ./resources /opt

# grab geschichte
RUN sh /opt/install-geschichte.sh

# grab project
RUN git clone https://github.com/kordano/ceres-geschichte.git /opt/ceres-geschichte

# grab dependencies
RUN /opt/retrieve-deps

# grab ass
CMD ["/opt/start-ceres"]
