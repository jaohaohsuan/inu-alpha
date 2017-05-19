package com.inu.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.http.management.ClusterHttpManagement
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.util.Timeout
import com.inu.cluster.storedquery.{StoredQueryRepoAggRoot, StoredQueryRepoView}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

object Main extends App {

  val config: Config = ConfigFactory.load()

  implicit val timeout              = Timeout(5.seconds)
  implicit val system               = ActorSystem(config.getString("storedq.cluster-name"), config)
  implicit val ec: ExecutionContext = system.dispatcher

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

    //system.log.info(s"running version ${com.inu.cluster.storedq.BuildInfo.version}")
  }

  val clusterMan = ClusterHttpManagement(cluster)
  clusterMan.start().onComplete { _ =>
    println("ClusterHttpManagement is up")
  }

  sys.addShutdownHook {
    clusterMan.stop()
    println("clusterHttpManagement is down")

    cluster.leave(cluster.selfAddress)
    cluster.down(cluster.selfAddress)
    system.terminate()
    Await.result(system.whenTerminated,Duration.Inf)
    println("actorsystem has shutdown gracefully")
  }

}