
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
    javaOptions in run ++= Seq("-Djava.library.path=./sigar"),
    mainClass in Compile := Some("boot.Main")

    )
).enablePlugins(DockerPlugin)

fork in run := true