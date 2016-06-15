import Library._
import com.typesafe.sbt.packager.docker._
import org.clapper.sbt.editsource.EditSourcePlugin.autoImport._
import sbt.Keys._
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

def create(title: String): Project = Project(title, file(title))
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
        dockerRepository      := Some("127.0.0.1:5000/inu"),
        buildInfoKeys := Seq[BuildInfoKey](name, version in ThisBuild, scalaVersion, sbtVersion)
        ): _*
    )

lazy val root = project.in(file(".")).settings(
  Seq(
    scalaVersion := Version.scala,
      sources in EditSource <++= baseDirectory.map{ d =>
    (d / "deployment" ** "*.yaml").get ++ (d / "deployment" ** "*.sh").get
  },
  targetDirectory in EditSource <<= baseDirectory(_ / "target"),
  flatten in EditSource := false,
  variables in EditSource <+= version { ver => ("version", ver )},
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    releaseStepTask(org.clapper.sbt.editsource.EditSourcePlugin.autoImport.clean in EditSource),
    releaseStepTask(org.clapper.sbt.editsource.EditSourcePlugin.autoImport.edit in EditSource)
  )
  )
).enablePlugins(GitVersioning, GitBranchPrompt)

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
    buildInfoPackage := s"com.inu.cluster.storedq",
    packageName in Docker := "storedq-cluster",
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
      Cmd("ENTRYPOINT", s"bin/${name.value}")
    )
    ,bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "scripts" / "extra.sh" )
    ).enablePlugins(JavaAppPackaging, DockerPlugin, GitVersioning, GitBranchPrompt, BuildInfoPlugin)

lazy val frontend = create("frontend").
  dependsOn(protocol).
  settings(
  libraryDependencies ++= Seq(
    akkaCluster, akkaClusterTools,akkaClusterMetrics,
    elasticsearch,
    json4sExt,
    spray, sprayRouting,
    scalatest,
    scalazCore
  ),
    mainClass in Compile := Some("com.inu.frontend.Main"),
    packageName in Docker := "storedq-api",
    buildInfoPackage := s"com.inu.frontend.storedq",
    dockerCommands := Seq(
      Cmd("FROM", "java:8-jdk-alpine"),
      ExecCmd("RUN", "apk", "add", "--no-cache", "bash"),
      Cmd("WORKDIR", "/opt/docker"),
      Cmd("ADD", "opt/docker/lib /opt/docker/lib"),
      Cmd("ADD", "opt/docker/bin /opt/docker/bin"),
      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "."),
      Cmd("EXPOSE", "2551", "7879"),
      Cmd("USER", "daemon"),
      Cmd("ENTRYPOINT", s"bin/${name.value}")
    )
  ).
  enablePlugins(JavaAppPackaging, DockerPlugin, GitVersioning, GitBranchPrompt, BuildInfoPlugin)