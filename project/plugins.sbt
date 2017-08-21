logLevel := Level.Error

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.github.nscala-time" %% "nscala-time" % "2.12.0"
)

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")
