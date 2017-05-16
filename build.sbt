import Library._
import sbt.Keys._

lazy val cpJarsForDocker = taskKey[Unit]("prepare for building Docker image")

def create(title: String): Project = Project(title, file(title))
    .settings(
      Revolver.settings ++
      Seq(
        scalaVersion          := Version.scala,
        scalacOptions        ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions"),
        resolvers            ++= Dependencies.resolvers,
        exportJars            := true,
        libraryDependencies  ++= Seq(akkaSlf4j, logbackClassic),
        git.useGitDescribe    := false,
        git.formattedShaVersion := git.gitHeadCommit.value map { sha =>
          sys.env.get("BUILD_NUMBER") match {
            case Some(nu) => s"$nu-${sha.take(7)}"
            case _ => s"${sha.take(7)}"
          }
        },
        buildInfoKeys := Seq[BuildInfoKey](name, version in ThisBuild, scalaVersion, sbtVersion),
        cpJarsForDocker := {

          val dockerDir = (target in Compile).value / "docker"

          val jar = (packageBin in Compile).value
          IO.copyFile(jar, dockerDir / "app" / jar.name)

          (dependencyClasspath in Compile).value.files.foreach { f => IO.copyFile(f, dockerDir / "libs" / f.name )}

          (mainClass in Compile).value.foreach { content => IO.write( dockerDir / "mainClass", content ) }
          IO.write( dockerDir / "tag", git.formattedShaVersion.value.getOrElse(version.value) )

          IO.copyFile(baseDirectory.value / "Dockerfile", dockerDir / "Dockerfile")
        }
        ): _*
    ).enablePlugins(BuildInfoPlugin)

lazy val root = project.in(file("."))

lazy val protocol = create("protocol")
  .settings(libraryDependencies ++= Seq(json4sNative, nscalaTime, kryo)
)

//lazy val buildNumber = sys.props.getOrElse("BUILD_NUMBER", default = "0")

lazy val cluster = create("cluster").
  dependsOn(protocol).
  settings(
    libraryDependencies ++= Seq(
      akkaCluster, akkaClusterTools,akkaClusterMetrics,
      akkaPersistence, akkaPersistenceCassandra,
      akkaPersistenceQuery, akkaHttpCore, akkaHttpExp,
      scalaLogging,
      scalatest,
      kryo
    ) ,
    buildInfoPackage := s"com.inu.cluster.storedq"
    )

lazy val frontend = create("frontend").
  dependsOn(protocol).
  settings(
  libraryDependencies ++= Seq(
    scalaLogging,
    akkaCluster, akkaClusterTools,akkaClusterMetrics,
    elasticsearch,
    json4sExt,
    spray, sprayRouting,
    scalatest,
    scalazCore,
    kryo
  ),
    buildInfoPackage := s"com.inu.frontend.storedq"
  )

