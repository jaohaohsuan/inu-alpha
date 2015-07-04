
resolvers ++= Dependencies.resolvers

libraryDependencies ++= Dependencies.projectTemplate

val project = Project(
  id = "inu-alpha",
  base = file("."),
  settings = Common.settings ++ Docker.settings ++ Seq(
    scalacOptions ++= Seq("-encoding", "UTF-8",
                                     "-deprecation",
                                     "-feature",
                                     "-unchecked"),
    javaOptions +="-Djava.library.path=lib/sigar",
    mainClass in Compile := Some("boot.Main"),
    cleanFiles += baseDirectory.value / "data"
    )
).enablePlugins(DockerPlugin).settings(Revolver.settings: _*)

fork in run := true

