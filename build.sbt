import Library._
import com.typesafe.sbt.packager.docker._
import sbt.Keys._

def create(name: String): Project = Project(name, file(name))
    .settings(
      Revolver.settings ++
      Seq(
        scalaVersion          := Version.scala,
        scalacOptions        ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions"),
        resolvers            ++= Dependencies.resolvers,
        libraryDependencies  ++= Seq(akkaSlf4j, logbackClassic),
        shellPrompt           := { state => ">> " },
        maintainer            := "Henry Jao",
        organization          := "com.inu",
        git.useGitDescribe    := true,
        dockerRepository      := Some("127.0.0.1:5000/inu")
        ): _*
    )

//lazy val common = create("common")

lazy val protocol = create("protocol")
  .settings(libraryDependencies ++= Seq(json4sNative, nscalaTime)
)

lazy val cluster = create("cluster")
  .dependsOn(protocol)
  .settings(
    libraryDependencies ++= Seq(
      akkaCluster, akkaClusterTools,akkaClusterMetrics,
      akkaPersistence, akkaPersistenceCassandra,
      akkaPersistenceQuery, akkaHttpCore, akkaHttpExp,
      kryo,
      scalatest
    ),
    packageName in Docker := packageName.value,
    mainClass in Compile := Some("com.inu.cluster.Main"),
    dockerCommands := Seq(
      Cmd("FROM", "java:8-jdk-alpine"),
      ExecCmd("RUN", "apk", "add", "--no-cache", "bash"),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "opt/docker/lib /opt/docker/lib"),
      Cmd("ADD", "opt/docker/bin /opt/docker/bin"),
      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
      Cmd("EXPOSE", "2551"),
      Cmd("USER", "daemon"),
      Cmd("ENTRYPOINT", s"bin/${packageName.value}")
    ),
    bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "scripts" / "extra.sh" )).
    enablePlugins(JavaAppPackaging, DockerPlugin, GitVersioning, GitBranchPrompt, BuildInfoPlugin)

lazy val frontend = create("frontend").
  dependsOn(protocol).
  settings(
  fork := true,
  libraryDependencies ++= Seq(
    akkaCluster, akkaClusterTools,akkaClusterMetrics,
    elasticsearch,
    json4sExt,
    spray, sprayRouting,
    scalatest,
    scalazCore
  )
  ).
  enablePlugins(JavaAppPackaging, DockerPlugin, GitVersioning, GitBranchPrompt, BuildInfoPlugin)