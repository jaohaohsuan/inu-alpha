FROM jaohaohsuan/jnlp-slave:0.0.7
MAINTAINER Henry Jao
COPY build.sbt ./
COPY project project/
COPY common common/
COPY protocol protocol/
COPY seed seed/
RUN chown -R jenkins:jenkins /home/jenkins
USER jenkins
RUN /usr/local/bin/sbt -v -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers 'project seed' 'compile' && \
    alias sbt='/usr/local/bin/sbt -v -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers'
VOLUME /home/jenkins
ENTRYPOINT ["jenkins-slave"]
