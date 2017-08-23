FROM fabric8/java-alpine-openjdk8-jre:1.2

RUN apk add --no-cache tzdata curl bash

ARG AKKA_VERSION
ENV AKKA_VERSION $AKKA_VERSION

ENV TZ=Asia/Taipei
ENV JAVA_CLASSPATH=/app/libs/*:/app/*

ARG JAVA_MAIN_CLASS
ENV JAVA_MAIN_CLASS $JAVA_MAIN_CLASS

ADD libs /app/libs
ADD app /app

ENTRYPOINT ["/deployments/run-java.sh"]