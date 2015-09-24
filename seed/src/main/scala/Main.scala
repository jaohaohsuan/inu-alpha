package seed

import akka.actor._
import common._
import elastic.ImplicitConversions._
import es.IndexScan
import org.elasticsearch.node.Node
import scala.util.{Failure, Success}

object Main extends App {

  val nodeConfig = NodeConfig parse args

  nodeConfig map { c =>

    implicit val system = ActorSystem(c.clusterName, c.config)
    import system._
    implicit val node = org.elasticsearch.node.NodeBuilder.nodeBuilder().settings(c.elasticsearch).node()

    log info s"${c}"

    if(c.isDataNode) {
      actorOf(es.Configurator.props) ! IndexScan
    }

    actorOf(Props(classOf[Configurator], node.client()), Configurator.Name)

    if(c.isEventsStore) {
      actorOf(Props[LeveldbJournalListener])
    }

    log info s"ActorSystem ${name} started successfully"
  }
}
