package boot

import akka.actor.{AddressFromURIString, _}
import akka.contrib.pattern.{ClusterReceptionistExtension, ClusterSharding, ClusterClient, ClusterSingletonManager}
import akka.io.IO
import akka.japi.Util.immutableSeq
import akka.pattern._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.can.Http
import worker._

import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {
    if(args.isEmpty){
      startBackend(2551)
      Thread.sleep(3000)
      startHttpApp(7879,0)
      //startElasticsearchApp
    }
  }

  def workTimeout = 10.seconds

  def startBackend(port: Int, sharedJournalStorePort: Int = 2551, role: String = "backend"): Unit = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", conf)

    startupSharedJournal(system, startStore = port == sharedJournalStorePort,
      ActorPath.fromString(s"akka.tcp://ClusterSystem@127.0.0.1:$sharedJournalStorePort/user/store"))

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props[domain.StoredQueryAggregateRoot],
      singletonName = "active",
      terminationMessage = PoisonPill,
      role = Some(role)
    ), name = "stored-query-aggregate-root")

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props[domain.StoredQueryItemsView],
      singletonName = "active",
      terminationMessage = PoisonPill,
      role = Some(role)
    ), name = "stored-query-items-view")

   }

  def startFrontend(port: Int): Unit = {
    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", conf)
    val frontend = system.actorOf(Props[Frontend], "frontend")
    system.actorOf(Props(classOf[WorkProducer], frontend), name = "producer")
    system.actorOf(Props[WorkResultConsumer], name = "consumer")
  }

  def startHttpApp(httpPort: Int, port: Int): Unit = {
    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.load("httpApp"))
    implicit val system = ActorSystem("HttpSystem", conf)

    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(addr) => system.actorSelection(RootActorPath(addr) / "user" / "receptionist")
    }

    val clusterClient = system.actorOf(ClusterClient.props(initialContacts.toSet), "clusterClient")

    val service = system.actorOf(Props(classOf[ServiceActor], clusterClient), "service")

    implicit val timeout = Timeout(5.seconds)
    IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = httpPort)
  }

  def startElasticsearchApp: Unit = {
    val conf = ConfigFactory.load("worker")
    implicit val system = ActorSystem("ElasticSystem", conf)

    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(addr) => system.actorSelection(RootActorPath(addr) / "user" / "receptionist")
    }

    val clusterClient = system.actorOf(ClusterClient.props(initialContacts.toSet), "clusterClient")

    system.actorOf(Props(classOf[domain.PercolatorWorker], clusterClient), "PercolatorWorker")

  }

  def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {
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
