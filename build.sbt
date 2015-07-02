
resolvers ++= Dependencies.resolvers

libraryDependencies ++= Dependencies.projectTemplate

val project = Project(
  id = "inu-alpha",
  base = file("."),
  settings = Common.settings ++ Docker.settings ++ Seq(
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8",
                                     "-deprecation",
                                     "-feature",
                                     "-unchecked"),
    javaOptions in Runtime +="-Djava.library.path=lib/sigar",
    unmanagedJars in Compile += file("lib/sigar/sigar-1.6.4.jar"),
    mainClass in Compile := Some("boot.Main"),
    cleanFiles += baseDirectory.value / "data"
    )
).enablePlugins(DockerPlugin).settings(Revolver.settings: _*)

fork in run := true

