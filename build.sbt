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
        libraryDependencies ++= Seq(scopt, akkaSlf4j, logbackClassic, scalazCore),
        shellPrompt          := { state => ">> " }
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
      akkaHttpCore, akkaHttpExp,
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
      Cmd("FROM", "anapsix/alpine-java:jdk8"),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "opt/docker/lib /opt/docker/lib"),
      Cmd("ADD", "opt/docker/bin /opt/docker/bin"),
      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
      Cmd("EXPOSE", "2551"),
      Cmd("USER", "daemon"),
      Cmd("ENTRYPOINT", s"bin/${name.value}")
    ),
    bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "scripts" / "extra.sh" ))
  .enablePlugins(JavaAppPackaging)