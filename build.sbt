
resolvers ++= Dependencies.resolvers

libraryDependencies ++= Dependencies.projectTemplate

val root = Project(
  id = "root",
  base = file("."),
  settings = Common.settings ++ Docker.settings ++ Seq(
    scalacOptions ++= Seq("-encoding", "UTF-8",
                                     "-deprecation",
                                     "-feature",
                                     "-unchecked"),
    mainClass in Compile := Some("boot.Main"),
    unmanagedJars in Compile += unmanagedBase.value / "sigar/sigar-1.6.4.jar",
    cleanFiles += baseDirectory.value / "data"
    )
).enablePlugins(DockerPlugin).settings(Revolver.settings: _*)

fork in run := true
