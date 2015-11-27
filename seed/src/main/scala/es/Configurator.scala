package es

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern._
import elastic.ImplicitConversions._
import es.indices._
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateResponse
import org.elasticsearch.client.Client

import scala.concurrent.Future

case object IndexScan
case class StoredQueryMappingResponse(responses: Seq[PutMappingResponse]) {

}

object Configurator {
  def props(implicit client: Client) = Props(classOf[es.Configurator], client)
}

/* node.client().admin().cluster().prepareClusterStats().execute().asFuture.onComplete {
       case Success(x) => system.log.info(s"data-node status: ${x.getStatus}")
       case Failure(e) => system.log.error(e, s"Unable to run elasticsearch data-node")
     }*/

class Configurator(implicit val client: Client) extends Actor with ActorLogging {

  import context.dispatcher

  def predicate(condition: Boolean)(fail: Exception): Future[Unit] =
    if (condition) Future( () ) else Future.failed(fail)

  def receive = {
    case IndexScan =>
      storedQuery.exists.execute().asFuture.map(_.isExists).flatMap {
        case false => storedQuery.create.asFuture
        case true =>
          log.info(s"${storedQuery.index} exists")
          for {
            r1 <- storedQuery.mapping.asFuture
            r2 <- storedQuery.putSourceMapping("ytx").asFuture
            r3 <- storedQuery.putSourceMapping("ami-l8k").asFuture
          } yield StoredQueryMappingResponse(Seq(r1, r2, r3))

      } pipeTo self

      logs.putIndexTemplate.asFuture pipeTo self

      (for {
        existResp <- storedFilter.exists.execute().asFuture
        _ <- predicate(existResp.isExists)(new Exception(s"${storedFilter.index} doesn't exist"))
      } yield existResp).recoverWith { case _ => storedFilter.create.asFuture }



    case r: IndicesExistsResponse if r.isExists =>
      log.info(s"indices exists")

    case r: CreateIndexResponse if r.isAcknowledged =>
      log.info(s"index created")
      sender ! IndexScan

    case StoredQueryMappingResponse(responses) =>
      log.info(s"$responses")

    case r: PutMappingResponse if r.isAcknowledged =>
      log.info(s"mapping updated")

    case r: PutIndexTemplateResponse if r.isAcknowledged =>
      log.info(s"indexTemplate updated")
      
    case Failure(ex) =>
      log.error(ex ,s"elasticsearch checkup error")
    case unknown =>
      log.warning(unknown.getClass.getName)

  }

}
