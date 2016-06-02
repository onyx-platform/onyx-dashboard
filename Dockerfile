FROM ubuntu:14.04.2

MAINTAINER Lucas Bradstreet <lucasbradstreet@gmail.com>

# Add a repo where OpenJDK can be found.
RUN apt-get install -y software-properties-common && \
add-apt-repository -y ppa:webupd8team/java && \
apt-get update && \
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections && \
apt-get install -y oracle-java8-installer && python2.7

COPY /target/onyx-dashboard.jar /

EXPOSE 3000

CMD sh -c "java -server -jar /onyx-dashboard.jar $ZOOKEEPER_ADDR"
