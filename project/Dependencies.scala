import sbt.Keys._
import sbt._

object Version {
  val akka = "2.3.9"
  val scala = "2.11.6"
  val spray = "1.3.3"
}

object Library {
  val akkaActor            = "com.typesafe.akka"      %% "akka-actor"                    % Version.akka
  val akkaSlf4j            = "com.typesafe.akka"      %% "akka-slf4j"                    % Version.akka
  val akkaPersistence      = "com.typesafe.akka"      %% "akka-persistence-experimental" % Version.akka
  val akkaCluster          = "com.typesafe.akka"      %% "akka-cluster"                  % Version.akka
  val akkaContrib          = "com.typesafe.akka"      %% "akka-contrib"                  % Version.akka
  val spray                = "io.spray"               %% "spray-can"                     % Version.spray
  val sprayRouting         = "io.spray"               %% "spray-routing"                 % Version.spray
  val logbackClassic       = "ch.qos.logback"         %  "logback-classic"               % "1.1.2"
  val sigar = "org.fusesource" % "sigar" % "1.6.4"
  val json4sNative             = "org.json4s"             %% "json4s-native"                    % "3.2.10"
  val scalaJsonCollection      = "net.hamnaberg.rest"     %% "scala-json-collection"            % "2.3"
  val elastic4s                = "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.6.0"
  val hashids = "com.timesprint" %% "hashids-scala" % "1.0.0"
  val log4j = "log4j" % "log4j" % "1.2.17"
}

object Dependencies {
  
  import Library._

  val resolvers = Seq(
    "Spray Repository"    at "http://repo.spray.io/"
  )

  val projectTemplate = List(
    akkaActor,
    akkaPersistence,
    akkaCluster,
    akkaContrib,
    akkaSlf4j,
    spray,
    sprayRouting,
    sigar,
    json4sNative,
    scalaJsonCollection,
    elastic4s,
    logbackClassic,
    hashids,
    log4j
  )
}
