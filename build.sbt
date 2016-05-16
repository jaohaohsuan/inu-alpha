import Library._
import com.typesafe.sbt.packager.docker._
import sbt.Keys._

def create(name: String): Project = Project(name, file(name))
    .settings(
      Revolver.settings ++
      Seq(
        scalaVersion         := Version.scala,
        scalacOptions       ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked"),
        resolvers           ++= Dependencies.resolvers,
        libraryDependencies ++= Seq(scopt, akkaSlf4j, logbackClassic, scalazCore)
        ): _*
    )

lazy val common = create("common").settings(
  libraryDependencies ++= Seq(elasticsearch, scalaLogging)
)

lazy val protocol = create("protocol")
  .dependsOn(common)
  .settings(libraryDependencies ++= Seq(akkaClusterTools, json4sNative, nscalaTime)
)

lazy val seed = create("seed")
  .dependsOn(common, protocol)
  .settings(
    libraryDependencies ++= Seq(
      spray, sprayRouting,
      akkaCluster, akkaClusterTools,
      akkaPersistenceQuery, akkaPersistenceCassandra,
      akkaClusterMetrics,
      elasticsearch,
      nscalaTime,
      kryo,
      scalatest
    ),
    mainClass in Compile := Some("seed.Main"),
    dockerRepository := Some("127.0.0.1:5000/inu"),
    version in Docker := "latest",
    packageName in Docker := "storedq",
    dockerCommands := Seq(
      Cmd("FROM", "alpine:3.3"),
      Cmd("ENV", "LANG C.UTF-8"),
      Cmd("RUN",
        """{ \
          |echo '#!/bin/sh'; \
          |echo 'set -e'; \
          |echo; \
          |echo 'dirname "$(dirname "$(readlink -f "$(which javac || which java)")")"'; \
          |} > /usr/local/bin/docker-java-home \
          |&& chmod +x /usr/local/bin/docker-java-home
        """.stripMargin),
      Cmd("ENV", "JAVA_HOME /usr/lib/jvm/java-1.8-openjdk/jre"),
      Cmd("ENV", "PATH $PATH:$JAVA_HOME/bin"),
      Cmd("ENV", "JAVA_VERSION 8u92"),
      Cmd("ENV", "JAVA_ALPINE_VERSION 8.92.14-r0"),
      Cmd("RUN",
        """set -x \
          |&& apk add --no-cache \
          |openjdk8-jre="$JAVA_ALPINE_VERSION" \
          |&& [ "$JAVA_HOME" = "$(docker-java-home)" ]
        """.stripMargin),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "opt /opt"),
      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
      Cmd("EXPOSE", "2551"),
      Cmd("USER", "daemon"),
      Cmd("ENTRYPOINT", s"bin/${name.value}")
    ),
    bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "scripts" / "extra.sh" )
  ).enablePlugins(JavaAppPackaging)

