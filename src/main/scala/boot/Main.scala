package boot

import akka.actor._
import akka.io.IO
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.can.Http

import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {
    if(args.isEmpty){
      startBackend(2551)
      startHttpApp(7879,0)
      startPercolatorWorker()
    }
  }

  def startBackend(port: Int, sharedJournalStorePort: Int = 2551, role: String = "backend"): Unit = {
    val conf = ConfigFactory.parseString(s"akka.cluster.roles=[$role]").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")).
      withFallback(ConfigFactory.load())
    implicit val system = ActorSystem("ClusterSystem", conf)

    ClusterBoot.boot(role, port, sharedJournalStorePort)
   }

  def startHttpApp(httpPort: Int, port: Int): Unit = {

    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.load("rest"))
    implicit val system = ActorSystem("HttpSystem", conf)

    val service = system.actorOf(Props[ServiceActor], "service")

    implicit val timeout = Timeout(5.seconds)
    IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = httpPort)
  }

  def startPercolatorWorker(): Unit = {
    val conf = ConfigFactory.load("worker")
    implicit val system = ActorSystem("ElasticSystem", conf)

    system.actorOf(Props(classOf[domain.PercolatorWorker], ClusterBoot.client(conf), Some(ClusterBoot.node)), "PercolatorWorker")
  }
}
