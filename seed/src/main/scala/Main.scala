package seed

import java.net.InetAddress

import akka.actor._
import common._
import es.IndexScan
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.node.NodeBuilder

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig foreach { c =>

    implicit val system = ActorSystem(c.clusterName, c.config)
    import system._

    implicit val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().settings(c.elasticsearch).node()
    implicit val client = node.client()

    log info s"Scopt: $c"

    actorOf(Props(classOf[Configurator], client), Configurator.Name)

    actorOf(es.Configurator.props) ! IndexScan

    if(c.isEventsStore) {
      actorOf(Props[LeveldbJournalListener])
    }

    log info s"ActorSystem $name started successfully"

    sys.addShutdownHook(system.terminate())
  }
}
