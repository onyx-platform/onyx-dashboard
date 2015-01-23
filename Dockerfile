FROM dockerfile/java:oracle-java8

MAINTAINER Lucas Bradstreet <lucasbradstreet@gmail.com>

COPY /target/onyx-dashboard.jar /

EXPOSE 8888

CMD ["java", "-server", "-jar", "/onyx-dashboard.jar"]
