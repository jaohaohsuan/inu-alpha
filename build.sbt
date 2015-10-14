import Library._
import NativePackagerHelper._

val installElasticsearch = taskKey[File]("Install Elasticsearch")

installElasticsearch := {
  val location = baseDirectory.value / "var"
  location
}

def InuProject(name: String): Project = Project(name, file(name))
    .settings(
      Revolver.settings ++
      Seq(
        scalaVersion := Version.scala,
        scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked"),
        resolvers ++= Dependencies.resolvers,
        libraryDependencies ++= Seq(scopt, akkaSlf4j, logbackClassic, scalazCore),
        fork in run := true): _*
    )


lazy val common = InuProject("common").settings(
  libraryDependencies ++= Seq(elasticsearch)
)

lazy val protocol = InuProject("protocol")
  .settings(
    libraryDependencies ++= Seq(akkaClusterTools, json4sNative, scalaJsonCollection)
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
      nscalaTime
    ),
    dockerExposedPorts := Seq(9200, 9300, 9301, 7879),
    packageName in Docker := "inu",
    version in Docker := "latest",
    dockerRepository := Some("jaohaohsuan"),
   /* mappings in Universal <+= (packageBin in Compile, baseDirectory ) map { (_, src) =>
      val conf = src / "var" / "elastic" / "config" / "elasticsearch.yml"
      conf -> "var/elastic/config/elasticsearch.yml"
    },
    mappings in Universal ++= directory("var/elastic/config"),*/
    cleanFiles += baseDirectory.value / "var" / "elastic" / "data" )
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
//fork in run := true
