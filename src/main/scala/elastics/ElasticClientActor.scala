package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticClient
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.action.support.master.AcknowledgedResponse

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ElasticClientActor {
  case object Install
}

class ElasticClientActor(node: Option[org.elasticsearch.node.Node]) extends Actor
  with LteTemplate
  with PercolatorIndex
  with util.ImplicitActorLogging {

  import ElasticClientActor._

  lazy val client: ElasticClient = node.map { com.sksamuel.elastic4s.ElasticClient.fromNode }.getOrElse(
    com.sksamuel.elastic4s.ElasticClient.remote("127.0.0.1", 9300)
  )

  import context.dispatcher

  def receive = {
    case Install =>

      val tasks = Seq(
        `PUT _template/lte`,
        `PUT inu-percolate`
      )

      Future.traverse(tasks) { task => task }.onComplete {
        case Success(results) =>
          results.foreach {
            case (name, resp:AcknowledgedResponse)=>
              log.info(s"$name acknowledged ${resp.isAcknowledged}")
            case (name, resp:IndicesExistsResponse)=>
              log.info(s"$name index exist ${resp.isExists}")
            case (name, resp) =>
              log.info(s"$name $resp")
          }
        case Failure(ex) => ex.logError(e=> s"$e")

      }

  }
}
