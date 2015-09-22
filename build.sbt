import Library._

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


lazy val common = InuProject("common")

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
      elastic4s,
      nscalaTime
    ),
    cleanFiles += baseDirectory.value / "data")
  .enablePlugins(JavaAppPackaging)

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
