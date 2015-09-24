package seed

import akka.actor.{Actor, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import domain.storedQuery.StoredQueryAggregateRoot
import spray.can.Http
import protocol.storedQuery.AggregateRoot
import elastic.ImplicitConversions._
import scala.util.{Success, Failure}

import scala.language.implicitConversions

object Configurator {
  val Name = "conf"
}

class Configurator(client: org.elasticsearch.client.Client) extends Actor with SharedLeveldbStoreUsage {

  import context.system

  def processReceive: Receive = {
    case LeveldbStoreRegistration(m) =>

      if(m.hasRole("compute")){
        system.actorOf(ClusterSingletonManager.props(
          singletonProps = Props(classOf[StoredQueryAggregateRoot]),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)),
          name = AggregateRoot.Name)
      }

      if(m.hasRole("sync")) {
        system.actorOf(Props(classOf[read.storedQuery.StoredQueryAggregateRootView], client)) ! "GO"
      }

      if(m.hasRole("web")) {
        system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = s"/user/${AggregateRoot.Name}",
          settings = ClusterSingletonProxySettings(system)
        ), name = "aggregateRootProxy")

        import frontend.ServiceActor
        import scala.concurrent.duration._
        val service = system.actorOf(Props(classOf[ServiceActor], client), "service")
        implicit val timeout = Timeout(5.seconds)
        IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = frontend.Config.port)
      }
    //ClusterClientReceptionist(system).registerService(storedQueryAggregateRoot)

    case unknown =>
  }

  def receive = registration orElse processReceive
}


