FROM jaohaohsuan/jnlp-slave:latest
MAINTAINER Henry Jao
ADD . ./
RUN echo "alias sbt='/usr/local/bin/sbt -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers'/" >> ~/.bashrc && \
    source ~/.bashrc && \
    sbt 'compile'
VOLUME /home/jenkins