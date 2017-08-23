import Library._

lazy val cpJarsForDocker = taskKey[Unit]("prepare for building Docker image")

def create(title: String): Project = Project(title, file(title))
    .settings(
      Seq(
        scalaVersion          := Version.scala,
        scalacOptions        ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions"),
        resolvers            ++= Dependencies.resolvers,
        libraryDependencies  ++= Seq(akkaSlf4j, logbackClassic),
        shellPrompt           := { state => ">> " },
        git.useGitDescribe    := false,
        git.formattedShaVersion := git.gitHeadCommit.value map { sha =>
          sys.env.get("BUILD_NUMBER") match {
            case Some(nu) => s"$nu-${sha.take(7)}"
            case _ => s"${sha.take(7)}"
          }
        },
        buildInfoKeys := Seq[BuildInfoKey](name, version in ThisBuild, scalaVersion, sbtVersion),
        fork in run in Global := true,
        exportJars := true,
        cpJarsForDocker := {

            val dockerDir = (target in Compile).value / "docker"

            val jar = (packageBin in Compile).value
            IO.copyFile(jar, dockerDir / "app" / jar.name)

            (dependencyClasspath in Compile).value.files.foreach { f => IO.copyFile(f, dockerDir / "libs" / f.name )}

            (mainClass in Compile).value.foreach { content => IO.write( dockerDir / "mainClass", content ) }
          
            IO.copyFile(baseDirectory.value / "Dockerfile", dockerDir / "Dockerfile")
          }
        ): _*
    )

lazy val root = project.in(file(".")).settings(
  Seq(
    scalaVersion := Version.scala
  )
).enablePlugins(GitVersioning)

lazy val protocol = create("protocol")
  .settings(libraryDependencies ++= Seq(json4sNative, nscalaTime, kryo)
)

lazy val cluster = create("cluster").
  enablePlugins(GitVersioning, BuildInfoPlugin).
  dependsOn(protocol).
  settings(
    libraryDependencies ++= Seq(
      akkaCluster, akkaClusterTools,akkaClusterMetrics,
      akkaPersistence, akkaPersistenceCassandra,
      akkaPersistenceQuery, akkaHttpCore, akkaHttpExp,
      scalaLogging,
      scalatest,
      kryo
    ),
    buildInfoPackage := s"com.inu.cluster.storedq",
    mainClass in Compile := Some("com.inu.cluster.Main")
  )

lazy val frontend = create("frontend").
  enablePlugins(GitVersioning, BuildInfoPlugin).
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
    mainClass in Compile := Some("com.inu.frontend.Main"),
    buildInfoPackage := s"com.inu.frontend.storedq"
  )