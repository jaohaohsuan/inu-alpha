package seed

import akka.actor._
import akka.persistence.journal.leveldb.{ SharedLeveldbStore, SharedLeveldbJournal }
import com.typesafe.config._
import java.net.{ InetAddress, NetworkInterface }
import scala.collection.JavaConversions._
import common._

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig map { c =>
    implicit val system = ActorSystem(c.clusterName, c.config)

    if(c.isEventsStore) {
      // Start the shared journal on one node (don't crash this SPOF)
      system.actorOf(Props[SharedLeveldbStore], "store")
    }

    system.actorOf(Props[SharedLeveldbStoreUsage], name = "conf")
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

    system.log info s"ActorSystem ${system.name} started successfully"
  }



}
