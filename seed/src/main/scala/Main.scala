package seed

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy}
import akka.persistence.journal.leveldb.{ SharedLeveldbStore, SharedLeveldbJournal }
import akka.stream.ActorMaterializer
import com.typesafe.config._
import java.net.{ InetAddress, NetworkInterface }
import protocol.storedQuery.AggregateRoot
import seed.domain.StoredQueryAggregateRoot.CreateNewStoredQuery

import scala.collection.JavaConversions._
import common._

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig map { c =>
    implicit val system = ActorSystem(c.clusterName, c.config)
    import system.dispatcher

    system.actorOf(Props[Configurator], Configurator.Name)

    if(c.isEventsStore)
      system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

    // persistence query
    import akka.persistence.query.PersistenceQuery
    import akka.persistence.query.journal.leveldb.LeveldbReadJournal

    val readJournal = PersistenceQuery(system).readJournalFor(LeveldbReadJournal.Identifier)

    import akka.persistence.query.EventsByPersistenceId
    import akka.stream.scaladsl.Source
    import akka.persistence.query.EventEnvelope

    val source: Source[EventEnvelope, Unit] =
      readJournal.query(EventsByPersistenceId(AggregateRoot.Name))

    implicit val mat = ActorMaterializer()
    source.runForeach { event => println(s"Event: ${event}")}


    system.log info s"ActorSystem ${system.name} started successfully"
  }
}
