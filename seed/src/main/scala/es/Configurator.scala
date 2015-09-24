package es

import akka.actor.{Props, ActorLogging, Actor}
import elastic.ImplicitConversions._
import es.indices._
import akka.pattern._
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateResponse
import org.elasticsearch.client.Client
import org.elasticsearch.node.Node

case object IndexScan


object Configurator {
  def props(implicit node: Node) = Props(classOf[es.Configurator], node.client())
}

/* node.client().admin().cluster().prepareClusterStats().execute().asFuture.onComplete {
       case Success(x) => system.log.info(s"data-node status: ${x.getStatus}")
       case Failure(e) => system.log.error(e, s"Unable to run elasticsearch data-node")
     }*/
/**
 * Created by henry on 9/24/15.
 */
class Configurator(implicit val client: Client) extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case IndexScan =>
      storedQuery.exists.asFuture.map(_.isExists).flatMap {
        case false => storedQuery.create.asFuture
        case true => storedQuery.mapping.asFuture
      } pipeTo self

      logs.putIndexTemplate.asFuture pipeTo self

    case r: CreateIndexResponse if r.isAcknowledged =>
      log.info(s"index created")
      sender ! IndexScan

    case r: PutMappingResponse if r.isAcknowledged =>
      log.info(s"mapping updated")

    case r: PutIndexTemplateResponse if r.isAcknowledged =>
      log.info(s"indexTemplate updated")
      
    case unexpected =>
      log.error(s"$unexpected")

  }

}
