import sbt._

object Version {
  val akka  = "2.4.0-RC2"
  val scala = "2.11.6"
  val spray = "1.3.3"
  val elasticsearch = "2.0.0-beta2"
}

object Library {
  val akkaActor                = "com.typesafe.akka"      %% "akka-actor"                    % Version.akka
  val akkaRemote               = "com.typesafe.akka"      %% "akka-remote"                   %  Version.akka
  val akkaSlf4j                = "com.typesafe.akka"      %% "akka-slf4j"                    % Version.akka
  val akkaPersistenceQuery     = "com.typesafe.akka"      %% "akka-persistence-query-experimental" % Version.akka
  val akkaPersistence          = "com.typesafe.akka"      %% "akka-persistence"              % Version.akka
  val akkaClusterTools         = "com.typesafe.akka"      %% "akka-cluster-tools"            % Version.akka
  val akkaCluster              = "com.typesafe.akka"      %% "akka-cluster"                  % Version.akka
  val akkaClusterMetrics       = "com.typesafe.akka"      %% "akka-cluster-metrics"          % Version.akka

  val word = "org.apdplat" % "word" % "1.3"
  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.12"
  val spray                    = "io.spray"               %% "spray-can"                     % Version.spray
  val sprayRouting             = "io.spray"               %% "spray-routing"                 % Version.spray
  //val sprayTestkit             = "io.spray"               %% "spray-testkit"                 % Version.spray % "test"
  val logbackClassic           = "ch.qos.logback"         %  "logback-classic"               % "1.1.3"
  //val sigar                    = "org.fusesource"         %  "sigar"                         % "1.6.4" classifier("native") classifier("")
  val json4sNative             = "org.json4s"             %% "json4s-native"                 % "3.2.10"
  val scalaJsonCollection      = "net.hamnaberg.rest"     %% "scala-json-collection"         % "2.3"
  //val elastic4s                = "com.sksamuel.elastic4s"    %% "elastic4s-core"                % "1.7.+"
  val elasticsearch           = "org.elasticsearch" % "elasticsearch" % Version.elasticsearch
  val hashids                  = "com.timesprint"            %% "hashids-scala"                 % "1.0.0"
 // val log4j                    = "log4j"                     %  "log4j"                         % "1.2.17"
  val nscalaTime               = "com.github.nscala-time"    %% "nscala-time"                   % "2.0.0"
  val scopt                    = "com.github.scopt"          %% "scopt"                         % "3.3.0"
  //val akkaPersistenceCassandra = "com.github.krasserm"       %% "akka-persistence-cassandra"    % "0.3.9"
  val leveldb                  = "org.iq80.leveldb"          % "leveldb"                        % "0.7"
  val leveldbjniAll            = "org.fusesource.leveldbjni" % "leveldbjni-all"              % "1.8"
  val shapeless  = "com.chuusai" %% "shapeless" % "2.2.5"
  val scalazCore = "org.scalaz" %% "scalaz-core" % "7.1.4"
  val elasticsearchGroovy = "org.elasticsearch" % "elasticsearch-groovy" % Version.elasticsearch
  val luceneExpressions = "org.apache.lucene" % "lucene-expressions" % "5.2.1"
  val jna = "net.java.dev.jna" % "jna" % "4.1.0"


}

object Dependencies {

  val resolvers = Seq(
    Resolver.sonatypeRepo("releases"),
    "Spray Repository"    at "http://repo.spray.io/",
    "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
    "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"
  )
}
