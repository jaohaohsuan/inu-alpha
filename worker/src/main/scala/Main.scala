package worker

import akka.actor._
import akka.cluster.client.ClusterClient
import percolator.PercolatorWorker

object Main extends App {

  val nodeConfig = WorkerConfig parse args

  nodeConfig map { c =>
    implicit val system = ActorSystem("PercolatorSync", c.config)

    val clusterClient = system.actorOf(ClusterClient.props(c.clusterClientSettings), "client")

    system.actorOf(Props(classOf[PercolatorWorker], clusterClient))

    system.log info s"ActorSystem ${system.name} started successfully"

  }
}
