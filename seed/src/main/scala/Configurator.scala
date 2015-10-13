package seed

import akka.actor.{Actor, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import domain.storedQuery.StoredQueryAggregateRoot
import spray.can.Http
import frontend.ServiceActor
import scala.concurrent.duration._
import elastic.ImplicitConversions._
import scala.util.{Success, Failure}

import scala.language.implicitConversions

object Configurator {
  val Name = "conf"
}

class Configurator(private implicit val client: org.elasticsearch.client.Client) extends Actor with SharedLeveldbStoreUsage {

  import context.system
  implicit val timeout = Timeout(5.seconds)

  def processReceive: Receive = {
    case LeveldbStoreRegistration(m) =>

      if(m.hasRole("compute")){
        system.actorOf(ClusterSingletonManager.props(
          singletonProps = Props(classOf[StoredQueryAggregateRoot]),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)),
          name = protocol.storedQuery.NameOfAggregate.Root)
      }

      if(m.hasRole("sync")) {

        import read.storedQuery._
        system.actorOf(ClusterSingletonManager.props(
          singletonProps = StoredQueryAggregateRootView.props,
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)
        ), protocol.storedQuery.NameOfAggregate.view.name)

      }

      if(m.hasRole("web")) {
        system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = s"/user/${protocol.storedQuery.NameOfAggregate.Root}",
          settings = ClusterSingletonProxySettings(system)
        ), name = "aggregateRootProxy")

        system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = protocol.storedQuery.NameOfAggregate.view.manager,
          settings = ClusterSingletonProxySettings(system)
        ), protocol.storedQuery.NameOfAggregate.view.proxy)


        val service = system.actorOf(Props(classOf[ServiceActor], client), "service")
        IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = frontend.Config.port)
      }
    //ClusterClientReceptionist(system).registerService(storedQueryAggregateRoot)

    case unknown =>
  }

  def receive = registration orElse processReceive
}


