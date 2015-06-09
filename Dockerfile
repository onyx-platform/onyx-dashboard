FROM ubuntu:14.04.2

MAINTAINER Lucas Bradstreet <lucasbradstreet@gmail.com>

# Add a repo where OpenJDK can be found.
RUN apt-get install -y software-properties-common
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update

# Auto-accept the Oracle JDK license
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections

RUN apt-get install -y oracle-java8-installer

COPY /target/onyx-dashboard.jar /

EXPOSE 3000

CMD ["java", "-server", "-jar", "/onyx-dashboard.jar"]
