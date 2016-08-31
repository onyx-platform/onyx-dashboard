FROM frolvlad/alpine-oraclejdk8:full
MAINTAINER Gardner Vickers <gardner@vickers.me>

ADD https://github.com/just-containers/s6-overlay/releases/download/v1.11.0.1/s6-overlay-amd64.tar.gz /tmp/

RUN tar xzf /tmp/s6-overlay-amd64.tar.gz -C /
RUN apk add --update bash
RUN rm -rf /tmp/*

COPY scripts/run.sh /opt/run.sh
COPY target/onyx-dashboard.jar /opt/onyx-dashboard.jar

ENTRYPOINT ["/init"]
EXPOSE 3000

CMD ["opt/run.sh"]
