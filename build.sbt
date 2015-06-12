resolvers ++= Dependencies.resolvers

libraryDependencies ++= Dependencies.projectTemplate

val project = Project(
  id = "inu",
  base = file("."),
  settings = Common.settings ++ Seq(
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked"),
    javaOptions in run ++= Seq(
      "-Djava.library.path=./sigar")
    )
)


fork in run := true