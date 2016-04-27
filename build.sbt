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
      kryo
    ),
    dockerRepository := Some("127.0.0.1:5000/inu"),
    packageName in Docker := "storedq",
    dockerCommands := Seq(
      Cmd("FROM", "java:latest"),
      Cmd("ENV", "REFRESHED_AT", "2016-04-08"),
      Cmd("RUN", "apt-get update && apt-get install -y apt-utils dnsutils && apt-get clean && rm -rf /var/lib/apt/lists/*"),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "opt", "/opt"),
      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
      Cmd("EXPOSE", "2551"),
      Cmd("USER", "daemon"),
      Cmd("ENTRYPOINT", "bin/storedq")
    ),
    bashScriptExtraDefines += """
                                |my_ip=$(hostname --ip-address)
                                |
                                |function format {
                                |  local fqdn=$1
                                |
                                |  local result=$(host $fqdn | \
                                |    grep -v "not found" | grep -v "connection timed out" | \
                                |    grep -v $my_ip | \
                                |    sort | \
                                |    head -5 | \
                                |    awk '{print $4}' | \
                                |    xargs | \
                                |    sed -e 's/ /,/g')
                                |  if [ ! -z "$result" ]; then
                                |    export $2=$result
                                |  fi
                                |}
                                |
                                |format $PEER_DISCOVERY_SERVICE SEED_NODES
                                |format $AKKA_PERSISTENCE_SERVICE CASSANDRA_NODES
                                |""".stripMargin)
  .enablePlugins(JavaAppPackaging)

