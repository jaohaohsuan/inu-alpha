import sbt._

object Version {
  val akka          = "2.4.16"
  val akkaHttp      = "10.0.0"
  val scala         = "2.11.8"
  val spray         = "1.3.4"
  val elasticsearch = "2.3.3"
  val elasticsearch5 = "5.2.1"
}

object Library {
  val akkaActor                = "com.typesafe.akka"           %% "akka-actor"                           % Version.akka
  val akkaRemote               = "com.typesafe.akka"           %% "akka-remote"                          % Version.akka
  val akkaSlf4j                = "com.typesafe.akka"           %% "akka-slf4j"                           % Version.akka
  val akkaPersistenceQuery     = "com.typesafe.akka"           %% "akka-persistence-query-experimental"  % Version.akka
  val akkaPersistence          = "com.typesafe.akka"           %% "akka-persistence"                     % Version.akka
  val akkaClusterTools         = "com.typesafe.akka"           %% "akka-cluster-tools"                   % Version.akka
  val akkaCluster              = "com.typesafe.akka"           %% "akka-cluster"                         % Version.akka
  val akkaClusterMetrics       = "com.typesafe.akka"           %% "akka-cluster-metrics"                 % Version.akka
  val akkaHttpCore             = "com.typesafe.akka"           %% "akka-http-core"                       % Version.akkaHttp
  val akkaHttpExp              = "com.typesafe.akka"           %% "akka-http"                            % Version.akkaHttp
  val akkaHttpJacksonExp       = "com.typesafe.akka"           %% "akka-http-jackson"                    % Version.akkaHttp
  val akkaHttpSprayJsonExp     = "com.typesafe.akka"           %% "akka-http-spray-json"                 % Version.akkaHttp
  val akkaHttpXmlExp           = "com.typesafe.akka"           %% "akka-http-xml"                        % Version.akkaHttp
  val akkaPersistenceCassandra = "com.typesafe.akka"           %% "akka-persistence-cassandra"           % "0.21"

  val sourceCode               = "com.lihaoyi"                 %% "sourcecode"                           % "0.1.1"
  val kubernetes               = "io.fabric8.forge"             % "kubernetes"                           % "2.2.211"
  val spray                    = "io.spray"                    %% "spray-can"                            % Version.spray
  val sprayRouting             = "io.spray"                    %% "spray-routing"                        % Version.spray
  val sprayTestkit             = "io.spray"                    %% "spray-testkit"                        % Version.spray  % "test"
  val logbackClassic           = "ch.qos.logback"               % "logback-classic"                      % "1.1.7"
  val scalaLogging             = "com.typesafe.scala-logging"  %% "scala-logging"                        % "3.4.0"
  val json4sNative             = "org.json4s"                  %% "json4s-native"                        % "3.5.0"
  val json4sExt                = "org.json4s"                  %% "json4s-ext"                           % "3.5.0"
  val elasticsearch            = "org.elasticsearch"            % "elasticsearch"                        % Version.elasticsearch
  val nscalaTime               = "com.github.nscala-time"      %% "nscala-time"                          % "2.14.0"
  val scopt                    = "com.github.scopt"            %% "scopt"                                % "3.5.0"
  val shapeless                = "com.chuusai"                 %% "shapeless"                            % "2.3.2"
  val scalazCore               = "org.scalaz"                  %% "scalaz-core"                          % "7.2.7"
  val scalatest                = "org.scalatest"               %% "scalatest"                            % "3.0.1"        % "test"
}

object Dependencies {
  val resolvers = Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "Spray Repository"          at  "http://repo.spray.io/",
    "Akka Snapshot Repository"  at  "http://repo.akka.io/snapshots/",
    "krasserm at bintray"       at  "http://dl.bintray.com/krasserm/maven",
    "Artima Maven Repository"   at  "http://repo.artima.com/releases"
  )
}
