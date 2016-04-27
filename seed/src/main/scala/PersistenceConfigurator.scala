package seed

import com.typesafe.config.{Config, ConfigFactory}

/**
  * Created by henry on 4/7/16.
  */
object PersistenceConfigurator {

  implicit class CassandraSettings(config: Config) {

    def enableCassandraPlugin(): Config = {

      val cassandra_nodes = config.getString("storedq.cassandra-nodes")

      val not_found: PartialFunction[String, Array[String]] = { case "" => Array("127.0.0.1") }
      val found: PartialFunction[String, Array[String]] = { case x: String => x.split(",").map(_.trim) }

      val content = not_found.orElse(found)(cassandra_nodes).map {
        addr => s"""
             |cassandra-journal.contact-points += "$addr"
             |cassandra-snapshot-store.contact-points += "$addr"
             """.stripMargin
      }.mkString("\n")
        .concat(s"""
             |akka.persistence.journal.plugin = "cassandra-journal"
             |akka.persistence.snapshot-store.plugin = "cassandra-snapshot-store"
           """.stripMargin)

      ConfigFactory.parseString(content)
        .withFallback(config)
        .resolve()
    }
  }


}
