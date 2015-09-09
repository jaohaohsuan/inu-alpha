package seed

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy}
import akka.persistence.journal.leveldb.{ SharedLeveldbStore, SharedLeveldbJournal }
import akka.stream.ActorMaterializer
import com.typesafe.config._
import java.net.{ InetAddress, NetworkInterface }
import protocol.storedQuery.AggregateRoot

import scala.collection.JavaConversions._
import common._

import scala.concurrent.Future

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig map { c =>
    implicit val system = ActorSystem(c.clusterName, c.config)

    system.actorOf(Props[Configurator], Configurator.Name)

    if(c.isEventsStore)
      system.actorOf(Props[LeveldbJournalListener])


    system.log info s"ActorSystem ${system.name} started successfully"
  }
}
