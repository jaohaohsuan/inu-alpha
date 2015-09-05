package seed

import akka.actor._
import akka.persistence.journal.leveldb.{ SharedLeveldbStore, SharedLeveldbJournal }
import com.typesafe.config._
import java.net.{ InetAddress, NetworkInterface }
import scala.collection.JavaConversions._
import common.NodeConfig

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig map { c =>
    val system = ActorSystem(c.clusterName, c.config)

    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

    if(c.isEventsStore)
      system.actorOf(Props[SharedLeveldbStore], "store") // Start the shared journal on one node (don't crash this SPOF)

    startupSharedJournal(system)


    system.log info s"ActorSystem ${system.name} started successfully"

  }

  def startupSharedJournal(system: ActorSystem, path: ActorPath = ActorPath.fromString(s"akka.tcp://inu@10.0.1.3:2551/user/store")): Unit = {

    import akka.pattern._

    import akka.util.Timeout

    import scala.concurrent.duration._

    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    val f = system.actorSelection(path) ? Identify(None)
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) =>
        SharedLeveldbJournal.setStore(ref, system)
        system.log.info("Injecting the (remote {}) SharedLeveldbStore actor reference", ref)
      case _ =>
        system.log.error("Shared journal not started at {}", path)
        system.terminate()
    }

    f.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.terminate()
    }
  }

}
