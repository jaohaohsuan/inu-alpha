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
      Cmd("FROM", "java:latest"),
      Cmd("ENV", "REFRESHED_AT 2016-04-08"),
      Cmd("RUN", "apt-get update && apt-get install -y apt-utils dnsutils && apt-get clean && rm -rf /var/lib/apt/lists/*"),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "opt /opt"),
      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
      Cmd("EXPOSE", "2551"),
      Cmd("USER", "daemon"),
      Cmd("ENTRYPOINT", s"bin/${name.value}")
    ),
    bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "scripts" / "extra.sh" )
  ).enablePlugins(JavaAppPackaging)

