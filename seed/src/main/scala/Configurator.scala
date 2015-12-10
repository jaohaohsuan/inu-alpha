package seed

import akka.actor.{Actor, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import domain.storedFilter.StoredFilterAggregateRoot
import domain.storedQuery.StoredQueryAggregateRoot
import frontend.ServiceActor
import spray.can.Http
import scala.concurrent.duration._
import scala.language.implicitConversions

object Configurator {
  val Name = "conf"
  def props(roles: Seq[String])(implicit client: org.elasticsearch.client.Client) = Props(classOf[Configurator], roles)
}

class Configurator(private implicit val client: org.elasticsearch.client.Client) extends Actor with UseSharedLeveldbStore {

  import context.system
  implicit val timeout = Timeout(5.seconds)

  def processReceive: Receive = {
    case LeveldbStoreRegistration(m) =>

      if(m.hasRole("compute")){
        system.actorOf(ClusterSingletonManager.props(
          singletonProps = Props(classOf[StoredQueryAggregateRoot]),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)),
          name = s"${protocol.storedQuery.NameOfAggregate.root}")

        system.actorOf(ClusterSingletonManager.props(
          singletonProps = Props(classOf[StoredFilterAggregateRoot]),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)),
          name = s"${protocol.storedFilter.NameOfAggregate.root}"
        )
      }

      if(m.hasRole("sync")) {

        system.actorOf(ClusterSingletonManager.props(
          singletonProps = read.storedQuery.StoredQueryAggregateRootView.props,
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)
        ), protocol.storedQuery.NameOfAggregate.view.name)

        system.actorOf(ClusterSingletonManager.props(
          singletonProps = read.storedFilter.StoredFilterAggregateRootView.props,
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)
        ), protocol.storedFilter.NameOfAggregate.view.name)

      }

      if(m.hasRole("web")) {
        system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = protocol.storedQuery.NameOfAggregate.root.manager,
          settings = ClusterSingletonProxySettings(system)
        ), name = protocol.storedQuery.NameOfAggregate.root.proxy) ! StoredQueryAggregateRoot.Initial

        system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = protocol.storedQuery.NameOfAggregate.view.manager,
          settings = ClusterSingletonProxySettings(system)
        ), protocol.storedQuery.NameOfAggregate.view.proxy)

        system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = protocol.storedFilter.NameOfAggregate.root.manager,
          settings = ClusterSingletonProxySettings(system)
        ), name = protocol.storedFilter.NameOfAggregate.root.proxy)

        /*system.actorOf(ClusterSingletonProxy.props(
          singletonManagerPath = protocol.storedFilter.NameOfAggregate.view.manager,
          settings = ClusterSingletonProxySettings(system)
        ), name = protocol.storedFilter.NameOfAggregate.view.proxy)*/


        val service = system.actorOf(Props(classOf[ServiceActor], client), "service")
        IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = frontend.Config.port)
      }

    case unknown =>
      log.warning(s"$unknown")
  }

  def receive = registration orElse processReceive
}


