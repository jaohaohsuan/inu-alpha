package com.inu.cluster

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.util.Timeout
import com.inu.cluster.storedquery.{StoredQueryRepoAggRoot, StoredQueryRepoView}
import com.typesafe.config.{Config, ConfigFactory}
import NodeConfigurator._
import PersistenceConfigurator._
import akka.cluster.Cluster
import akka.cluster.client.ClusterClientReceptionist

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object Main extends App {

  val config: Config = ConfigFactory.load().onboard().enableCassandraPlugin()

  implicit val timeout = Timeout(5.seconds)
  implicit val system = ActorSystem(config.getString("storedq.cluster-name"), config)

  system.log.info("Configured seed nodes: " + config.getStringList("akka.cluster.seed-nodes").mkString(", "))
  system.log.info("Configured cassandra nodes: " + config.getStringList("cassandra-journal.contact-points").mkString(", "))
  system.actorOf(Props[ClusterMonitor], "cluster-monitor")

  implicit class clustering(props: Props) {
    def singleton()(implicit system: ActorSystem) = ClusterSingletonManager.props(
      singletonProps = props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system).withRole("backend"))
  }

  ClusterClientReceptionist(system).registerService(system.actorOf(StoredQueryRepoAggRoot.props.singleton(), "StoredQueryRepoAggRoot"))

  ClusterClientReceptionist(system).registerService(system.actorOf(StoredQueryRepoView.props.singleton(), "StoredQueryRepoView"))

  system.log.info(s"running version ${com.inu.cluster.storedq.BuildInfo.version}")

  val release = () => {
    system.terminate()
  }
  sys.addShutdownHook(release())
}