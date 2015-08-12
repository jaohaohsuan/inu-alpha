import sbt.Keys._
import sbt._
import sbtdocker.DockerPlugin.autoImport._
import sbtdocker.mutable.Dockerfile

object Docker {
  val settings = List(
    docker <<= docker.dependsOn(sbt.Keys.`package`.in(Compile, packageBin)),
    // Define a Dockerfile
    dockerfile in docker := {
      val jarFile = artifactPath.in(Compile, packageBin).value
      val sigar = baseDirectory.value / "lib/sigar"
      val `docker-entrypoint.sh`: File = baseDirectory.value / "docker-entrypoint.sh"
      val `logstash.conf`: File = baseDirectory.value / "logstash.conf"
      val classpath = (managedClasspath in Compile).value
      val mainclass = mainClass.in(Compile, packageBin).value.getOrElse(sys.error("Expected exactly one main class"))
      val libs = "/app/libs"
      val jarTarget = "/app/" + jarFile.name

      new Dockerfile {
        // Use a base image that contain Java
        from("java")

        // Expose ports
        expose(7879, 9200, 9300)

        // Copy all dependencies to 'libs' in the staging directory
        classpath.files.foreach { depFile =>
          val target = file(libs) / depFile.name
          stageFile(depFile, target)
        }
        // Add the libs dir from the
        addRaw(libs, libs)

        copy(sigar, "/app/sigar")
        copy(`docker-entrypoint.sh`, "/")
        run("chmod", "+x", "/docker-entrypoint.sh")

        env("LOGSTASH_VERSION", "1.5.3")
        env("MAVEN_VERSION", "3.3.3")
        env("ES_VERSION", "1.7.1")

        workDir("/tmp")

        runRaw(
          """wget -nv -c -t0 https://download.elastic.co/logstash/logstash/logstash-${LOGSTASH_VERSION}.tar.gz && \

             |curl -fsSL http://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xzf - -C /usr/share && \
             |wget -nv -c -t0 https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-$ES_VERSION.tar.gz""".stripMargin)

        runRaw("""mkdir /elk && \
                  |    tar -xzf ./logstash-${LOGSTASH_VERSION}.tar.gz && \
                  |    ls -al && \
                  |    rm ./logstash-${LOGSTASH_VERSION}.tar.gz && \
                  |    mv ./logstash-${LOGSTASH_VERSION} /elk/logstash  && \
                  |    pwd && \
                  |    ls -al /elk/logstash && \
                  |    /elk/logstash/bin/plugin install logstash-input-sttxml1 --version 0.0.5 && \
                  |    mv /usr/share/apache-maven-$MAVEN_VERSION /usr/share/maven && \
                  |    ln -s /usr/share/maven/bin/mvn /usr/bin/mvn && \
                  |    tar -xzf elasticsearch-$ES_VERSION.tar.gz && \
                  |    mv elasticsearch-$ES_VERSION /elk/elasticsearch && \
                  |    rm elasticsearch-$ES_VERSION.tar.gz""".stripMargin)


        env("MAVEN_HOME", "/usr/share/maven")


        runRaw(
          """git clone https://github.com/jaohaohsuan/elasticsearch-analysis-ik.git && \
            |mvn -q -f elasticsearch-analysis-ik/pom.xml compile package && \
            |/elk/elasticsearch/bin/plugin --install analysis-ik --url file:///tmp/elasticsearch-analysis-ik/target/releases/elasticsearch-analysis-ik-1.4.0.zip && \
            |rm -rf elasticsearch-analysis-ik""".stripMargin)


        copy(`logstash.conf`, "/elk/logstash/logstash-config/")
        // directory target is for akka-persistence use
        volume("/stt", "/elk/logstash/logstash-config", "/data", "/target")

        // Add the generated jar file
        add(jarFile, jarTarget)
        // The classpath is the 'libs' dir and the produced jar file
        val classpathString = s"$libs/*:$jarTarget"
        // Set the entry point to start the application using the main class
        workDir("/")
        entryPoint("/docker-entrypoint.sh")
        cmd("java", "-Djava.library.path=/app/sigar", "-cp", classpathString, mainclass)
      }
    },
    imageNames in docker := Seq(
      ImageName(namespace = Some(organization.value),
        repository = name.value,
        tag = Some("v" + version.value))),
    buildOptions in docker := BuildOptions(
      removeIntermediateContainers = BuildOptions.Remove.OnSuccess
    )
  )
}
