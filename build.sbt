import Library._
import org.clapper.sbt.editsource.EditSourcePlugin.autoImport._
import sbt.Keys._
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import com.github.nscala_time.time.Imports._
import xerial.sbt.Pack._

def create(title: String): Project = Project(title, file(title))
    .settings(
      packAutoSettings ++
      Revolver.settings ++
      Seq(
        scalaVersion          := Version.scala,
        scalacOptions        ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions"),
        resolvers            ++= Dependencies.resolvers,
        libraryDependencies  ++= Seq(akkaSlf4j, logbackClassic),
        shellPrompt           := { state => ">> " },
        git.useGitDescribe    := false,
        git.formattedShaVersion := git.gitHeadCommit.value map { sha => s"${sha.take(7)}" },
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
  .settings(libraryDependencies ++= Seq(json4sNative, nscalaTime, kryo)
)

lazy val cluster = create("cluster").
  enablePlugins(DockerPlugin, GitVersioning, GitBranchPrompt, BuildInfoPlugin).
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
    buildInfoPackage := s"com.inu.cluster.storedq",
    mainClass in docker := Some("com.inu.cluster.Main"),
    dockerfile in docker := {
      //val jarFile: File = sbt.Keys.`package`.in(Compile, packageBin).value
      val jarFiles = packLibJars.value.map { case (file, proj) => file }
      val classpath = (managedClasspath in Compile).value
      val mainclass = mainClass.in(docker).value.getOrElse("")

      //val classpathString = packLibJars.value.map{ case (file, _) => "/app/libs/" + file.getName }.mkString(":") + ":" + s"/app/${jarFile.getName}"
      val `modify@` = (format: String, file: File) => new DateTime(file.lastModified()).toString(format)

      new Dockerfile {
        from("openjdk:8-jre-alpine")
        runRaw("apk add --no-cache tzdata")
        classpath.files.groupBy(`modify@`("MM/dd/yyyy",_)).map { case (g, files) =>
          add(files, "/app/libs/")
        }

        packLibJars.value.map { case (file, proj) => add(file, "/app/") }

        //add(classpath.files, "/app/libs/")
        //add(jarFile, "/app/")
        //env("JAVA_OPTS", "")
        env("TZ", "Asia/Taipei")

        entryPoint("java", "-cp", "/app/libs/*:/app/*", mainclass)
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("127.0.0.1:5000/inu"),
        repository = "storedq-cluster",
        tag = Some(version.value)
      ),
      ImageName(
        namespace = Some("127.0.0.1:5000/inu"),
        repository = "storedq-cluster",
        tag = Some("latest")
      )
    )
    )

lazy val frontend = create("frontend").
  enablePlugins(DockerPlugin, GitVersioning, GitBranchPrompt, BuildInfoPlugin).
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
    mainClass in docker := Some("com.inu.frontend.Main"),
    buildInfoPackage := s"com.inu.frontend.storedq",
    dockerfile in docker := {
      //val jarFile: File = sbt.Keys.`package`.in(Compile).value
      val classpath = (managedClasspath in Compile).value
      val mainclass = mainClass.in(docker).value.getOrElse("")
      //val classpathString = classpath.files.map("/app/libs/" + _.getName).mkString(":") + ":" + s"/app/${jarFile.getName}"
      val `modify@` = (format: String, file: File) => new DateTime(file.lastModified()).toString(format)

      new Dockerfile {
        from("openjdk:8-jre-alpine")
        runRaw("apk add --no-cache tzdata")
        classpath.files.groupBy(`modify@`("MM/dd/yyyy",_)).map { case (g, files) =>
          add(files, "/app/libs/")
        }
        //add(classpath.files, "/app/libs/")
        //add(jarFile, "/app/")
        packLibJars.value.map { case (file, proj) => add(file, "/app/") }

        env("TZ", "Asia/Taipei")
        //env("JAVA_OPTS", "")
        entryPoint("java", "-cp", "/app/libs/*:/app/*", mainclass)
      }
    },
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("127.0.0.1:5000/inu"),
        repository = "storedq-api",
        tag = Some(version.value)
      ),
      ImageName(
        namespace = Some("127.0.0.1:5000/inu"),
        repository = "storedq-api",
        tag = Some("latest")
      )
    )

    //,bashScriptExtraDefines ++= IO.readLines(baseDirectory.value / "scripts" / "extra.sh" )
  )