logLevel := Level.Error

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.github.nscala-time" %% "nscala-time" % "2.12.0"
)

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.0")
//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
addSbtPlugin("org.clapper" % "sbt-editsource" % "0.7.0")
