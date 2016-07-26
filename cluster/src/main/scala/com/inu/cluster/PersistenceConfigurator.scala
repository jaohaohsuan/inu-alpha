package com.inu.cluster

import com.typesafe.config.{Config, ConfigFactory}

/**
  * Created by henry on 4/7/16.
  */
object PersistenceConfigurator {

  implicit class CassandraSettings(config: Config) {

    def enableCassandraPlugin(): Config = {

      val cassandra_nodes = Option(System.getenv("AKKA_PERSISTENCE_SERVICE")).getOrElse("")

      val not_found: PartialFunction[String, Array[String]] = { case "" => Array("127.0.0.1") }
      val found: PartialFunction[String, Array[String]] = { case x: String => x.split("""[\s,]+""").map(_.trim).filterNot(_.isEmpty) }

      val content0 =
        """akka.persistence.journal.plugin = "cassandra-journal"
          |akka.persistence.snapshot-store.plugin = "cassandra-snapshot-store"
          |cassandra-journal.contact-points = []
          |cassandra-snapshot-store.contact-points = []
        """.stripMargin

      val content1 = not_found.orElse(found)(cassandra_nodes).foldLeft(content0){ (acc, addr) =>
        s"""$acc
           |cassandra-journal.contact-points += "$addr"
           |cassandra-snapshot-store.contact-points += "$addr"
         """.stripMargin
       }

      ConfigFactory.parseString(content1)
        .withFallback(config)
        .resolve()
    }
  }


}
