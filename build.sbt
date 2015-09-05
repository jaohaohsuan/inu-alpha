
//libraryDependencies ++= Dependencies.projectTemplate

def InuProject(name: String): Project = {

  import Library._

  Project(name, file(name)).
  settings(
      scalaVersion := Version.scala,
      scalacOptions ++= Seq("-encoding", "UTF-8",
                                             "-deprecation",
                                             "-feature",
                                             "-unchecked"),
      resolvers ++= Dependencies.resolvers,
      libraryDependencies ++= Seq(
        scopt,
        akkaActor,
        akkaCluster,
        akkaPersistence, leveldb, leveldbjniAll,
        akkaClusterMetrics,
        akkaSlf4j, logbackClassic
      ),
      fork in run := true
    ).
  settings(Revolver.settings: _*)
}

lazy val common = InuProject("common")


lazy val seed = InuProject("seed").dependsOn(common)

lazy val worker = InuProject("worker").dependsOn(common)


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
