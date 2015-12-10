import Library._
import sbt.Keys._

def InuProject(name: String): Project = Project(name, file(name))
    .settings(
      Revolver.settings ++
      Seq(
        scalaVersion         := Version.scala,
        scalacOptions       ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked"),
        resolvers           ++= Dependencies.resolvers,
        libraryDependencies ++= Seq(scopt, akkaSlf4j, logbackClassic, scalazCore)
        ): _*
    )

lazy val api = Project("api", file("api")).settings(
  Revolver.settings ++
  Seq(
    scalaVersion         := "2.11.7",
    scalacOptions       ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-experimental"    % "2.0-M2",
      "com.typesafe.akka" %% "akka-http-core-experimental" % "2.0-M2",
      "com.typesafe.akka" %% "akka-http-experimental"      % "2.0-M2",
      "com.typesafe.akka" %% "akka-http-xml-experimental"  % "2.0-M2",
      "de.heikoseeberger" %% "akka-http-json4s"            % "1.3.0"
    ),
    resolvers            += "hseeberger at bintray" at "http://dl.bintray.com/hseeberger/maven"
  )
)

lazy val common = InuProject("common").settings(
  libraryDependencies ++= Seq(elasticsearch, scalaLogging)
)

lazy val protocol = InuProject("protocol")
  .dependsOn(common)
  .settings(libraryDependencies ++= Seq(akkaClusterTools, json4sNative, nscalaTime)
)

lazy val seed = InuProject("seed")
  .dependsOn(common, protocol)
  .settings(
    libraryDependencies ++= Seq(
      spray, sprayRouting,
      akkaCluster, akkaClusterTools,
      leveldb, leveldbjniAll, akkaPersistenceQuery,
      akkaClusterMetrics,
      elasticsearch, jna,
      nscalaTime,
      kryo
    ),
    dockerExposedPorts     := Seq(9200, 9300, 7879),
    dockerExposedVolumes   := Seq("/opt/docker/var/elastic/data", "/opt/docker/var/leveldb"),
    packageName in Docker  := "inu",
    version in Docker      := "latest",
    dockerRepository       := Some("jaohaohsuan"),
    /*dockerCommands      ++= Seq(
      // setting the run script executable
      ExecCmd("RUN",
        "chown", "-R", "elasticsearch:elasticsearch",
        s"${(defaultLinuxInstallLocation in Docker).value}/var/elastic")
    ),*/
   /* mappings in Universal <+= (packageBin in Compile, baseDirectory ) map { (_, src) =>
      val conf = src / "var" / "elastic" / "config" / "elasticsearch.yml"
      conf -> "var/elastic/config/elasticsearch.yml"
    },
    */
    cleanFiles ++= Seq(baseDirectory.value / "var" / "elastic" / "data", baseDirectory.value / "var" / "leveldb" ) )
  .enablePlugins(JavaAppPackaging)


//unmanagedClasspath in Runtime <+= (baseDirectory) map { bd => Attributed.blank(bd / "word") },

/*lazy val worker = InuProject("worker")
  .dependsOn(common, protocol)
  .settings(
    libraryDependencies ++= Seq(
      akkaRemote, akkaClusterTools,
      elastic4s)
)*/


//val root = Project(
//  id = "root",
//  base = file("."),
//  settings = Common.settings ++ Docker.settings ++ Seq(
//    scalacOptions ++= Seq("-encoding", "UTF-8",
//                                     "-deprecation",
//                                     "-feature",
//                                     "-unchecked"),
//    mainClass in Compile := Some("boot.Main"),
//    unmanagedJars in Compile += unmanagedBase.value / "sigar/sigar-1.6.4.jar",
//    cleanFiles += baseDirectory.value / "data"
//    )
//).enablePlugins(DockerPlugin).settings(Revolver.settings: _*)
//

