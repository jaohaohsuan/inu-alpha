package com.inu.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.util.Timeout
import com.inu.cluster.storedquery.{StoredQueryRepoAggRoot, StoredQueryRepoView}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object Main extends App {

  val config: Config = ConfigFactory.load()

  implicit val timeout = Timeout(5.seconds)
  implicit val system = ActorSystem(config.getString("storedq.cluster-name"), config)

  system.log.info("Configured seed nodes: " + config.getStringList("akka.cluster.seed-nodes").mkString(", "))
  system.log.info("Configured cassandra nodes: " + config.getStringList("cassandra-journal.contact-points").mkString(", "))

  implicit class clustering(props: Props) {
    def singleton(role: String = "backend")(implicit system: ActorSystem): Props = ClusterSingletonManager.props(
      singletonProps = props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system).withRole(role))
  }

  val cluster = Cluster(system)

  cluster.registerOnMemberUp {

    system.actorOf(StoredQueryRepoAggRoot.propsWithBackoff.singleton(), "StoredQueryRepoAggRoot")

    system.actorOf(StoredQueryRepoView.propsWithBackoff)

    system.log.info(s"running version ${com.inu.cluster.storedq.BuildInfo.version}")
  }

  sys.addShutdownHook {
    cluster.leave(cluster.selfAddress)
  }

}