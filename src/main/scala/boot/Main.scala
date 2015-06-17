package boot

import akka.actor.{AddressFromURIString, _}
import akka.contrib.pattern.{ClusterReceptionistExtension, ClusterSharding, ClusterClient, ClusterSingletonManager}
import akka.io.IO
import akka.japi.Util.immutableSeq
import akka.pattern._
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import domain.search.{StoredQueryRepo, DependencyGraph}
import spray.can.Http
import worker._

import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {
    if(args.isEmpty){
      startBackend(2551)
      //Thread.sleep(5000)
      //startBackend(2552)

      //startQueryTemplateUpdatingWorker(0)
      Thread.sleep(5000)
      startHttpApp(7879,0)
    }
  }

  def workTimeout = 10.seconds

  def startBackend(port: Int, sharedJournalStorePort: Int = 2551, role: String = "backend"): Unit = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("ClusterSystem", conf)

    startupSharedJournal(system, startStore = (port == sharedJournalStorePort),
      ActorPath.fromString(s"akka.tcp://ClusterSystem@127.0.0.1:$sharedJournalStorePort/user/store"))

//    system.actorOf(ClusterSingletonManager.props(
//      singletonProps = Master.props(workTimeout),
//      singletonName = "active",
//      terminationMessage = PoisonPill,
//      role = Some(role)
//    ), name = "master")

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = DependencyGraph.props,
      singletonName = "active",
      terminationMessage = PoisonPill,
      role = Some(role)
    ), name = "storedQueryDependencyGraph")

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = StoredQueryRepo.props,
      singletonName = "active",
      terminationMessage = PoisonPill,
      role = Some(role)
  ), name = "storedQueryRepo")

    val storedQueryRegion = ClusterSharding(system).start(
      typeName = domain.search.StoredQuery.shardName,
      entryProps = Some(domain.search.StoredQuery.props()),
      idExtractor = domain.search.StoredQuery.idExtractor,
      shardResolver = domain.search.StoredQuery.shardResolver
    )

    ClusterReceptionistExtension(system).registerService(storedQueryRegion)

    val storedQueryViewRegion = ClusterSharding(system).start(
      typeName = domain.search.StoredQueryView.shardName,
      entryProps = Some(domain.search.StoredQueryView.props()),
      idExtractor = domain.search.StoredQueryView.idExtractor,
      shardResolver = domain.search.StoredQueryView.shardResolver
    )

    ClusterReceptionistExtension(system).registerService(storedQueryViewRegion)
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
    IO(Http) ? Http.Bind(service, interface = "127.0.0.1", port = httpPort)
  }

  def startWorker(port: Int): Unit = {
    //load worker.conf
    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.load("worker"))
    val system = ActorSystem("WorkerSystem", conf)
    val initialContacts = immutableSeq(conf.getStringList("contact-points")).map {
      case AddressFromURIString(addr) => system.actorSelection(RootActorPath(addr) / "user" / "receptionist")
    }.toSet

    val clusterClient = system.actorOf(ClusterClient.props(initialContacts), "clusterClient")
    system.actorOf(Worker.props(clusterClient, Props[WorkExecutor]), "worker")
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
