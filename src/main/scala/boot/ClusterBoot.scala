package boot

import akka.actor._
import akka.contrib.pattern.{ClusterClient, ClusterSingletonManager}
import akka.japi.Util._

object ClusterBoot {

  def boot(role: String, port: Int, sharedJournalStorePort: Int = 2551)(implicit clusterSystem: ActorSystem): Unit = {

    startupSharedJournal(clusterSystem, startStore = port == sharedJournalStorePort,
      ActorPath.fromString(s"akka.tcp://ClusterSystem@127.0.0.1:$sharedJournalStorePort/user/store"))

    clusterSystem.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(classOf[domain.StoredQueryAggregateRoot]),
      singletonName = "active",
      terminationMessage = PoisonPill,
      role = Some(role)
    ), name = "stored-query-aggregate-root")

    clusterSystem.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(classOf[domain.StoredQueryItemsView], node),
      singletonName = "active",
      terminationMessage = PoisonPill,
      role = Some(role)
    ), name = "stored-query-items-view")

    sys.addShutdownHook {
      node.close()
    }
  }

  def client(conf: com.typesafe.config.Config)(implicit system: ActorSystem): ActorRef = {
    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(addr) => system.actorSelection(RootActorPath(addr) / "user" / "receptionist")
    }
    system.actorOf(ClusterClient.props(initialContacts.toSet), "clusterClient")
  }

  lazy val node: org.elasticsearch.node.Node = {
    import org.elasticsearch.node.NodeBuilder._

     nodeBuilder().node()
  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {

    import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
    import akka.util.Timeout
    import scala.concurrent.duration._
    import akka.pattern._

    // Start the shared journal on one node (don't crash this SPOF)
    if(startStore)
      system.actorOf(Props[SharedLeveldbStore], "store")

    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    val f = system.actorSelection(path) ? Identify(None)
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
      case _ =>
        system.log.error("Shared journal not started at {}", path)
        system.shutdown()
    }

    f.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", path)
        system.shutdown()
    }
  }
}
