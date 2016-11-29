FROM jaohaohsuan/jnlp-slave:latest
ADD . ./
RUN tar -xf /opt/sbt-caches.tar.gz \
  && sbt 'project root' 'release' 'clean' \
  && sbt 'project cluster' 'compile' 'clean' 'test' 'clean' \
  && sbt 'project frontend' 'compile' 'clean' \
  && tar -cvf /opt/sbt-caches.tar.gz .ivy2 .sbt \
  && rm -rf .ivy2 .sbt