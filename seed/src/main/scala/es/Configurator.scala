package es

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern._
import elastic.ImplicitConversions._
import es.indices._
import org.elasticsearch.action.ActionResponse
import org.elasticsearch.action.admin.indices.create.{CreateIndexResponse, CreateIndexRequestBuilder}
import org.elasticsearch.action.admin.indices.exists.indices.{IndicesExistsRequestBuilder, IndicesExistsResponse}
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

class Configurator(implicit val client: Client) extends Actor with ActorLogging {

  import context.dispatcher

  def predicate(condition: Boolean)(fail: Exception): Future[Unit] =
    if (condition) Future( () ) else Future.failed(fail)

  def createIndex(index: String) =
    (for {
      r <- client.admin().indices().prepareExists(index).execute().future
      _ <- predicate(r.isExists)(new Exception(s"$index doesn't exist"))
    } yield r).recoverWith {
      case _ => client.admin().indices()
                    .prepareCreate(index).setSettings(
                      """{
                        | "index" : {
                        |   "number_of_shards" : 1,
                        |   "number_of_replicas" : 1
                        | }
                        |}""".stripMargin).execute().future
    }.map { r => (index, r) }


  def receive = {
    case IndexScan =>
      storedQuery.exists.execute().future.map(_.isExists).flatMap {
        case false => storedQuery.create.future
        case true =>
          log.info(s"${storedQuery.index} exists")
          for {
            r1 <- storedQuery.mapping.future
            r2 <- storedQuery.putSourceMapping("ytx").future
            r3 <- storedQuery.putSourceMapping("ami-l8k").future
          } yield StoredQueryMappingResponse(Seq(r1, r2, r3))

      } pipeTo self

      logs.putIndexTemplate.future.map { r => ("template1", r) } pipeTo self

      createIndex(storedFilter.index) pipeTo self
      createIndex("internal") pipeTo self

    case (index: String, r: CreateIndexResponse)=>
      log.info(s"index:$index created:${r.isAcknowledged}")

    case (index: String,r: IndicesExistsResponse) =>
      log.info(s"index:$index exists:${r.isExists}")

    case r: IndicesExistsResponse if r.isExists =>
      log.info(s"indices exists")

    case r: CreateIndexResponse if r.isAcknowledged =>
      log.info(s"index created")
      sender ! IndexScan

    case StoredQueryMappingResponse(responses) =>
      log.info(s"$responses")

    case r: PutMappingResponse if r.isAcknowledged =>
      log.info(s"mapping updated")

    case (template: String ,r: PutIndexTemplateResponse) if r.isAcknowledged =>
      log.info(s"indexTemplate:$template updated")
      
    case Failure(ex) =>
      log.error(ex ,s"elasticsearch checkup error")

    case unknown =>
      log.warning(unknown.getClass.getName)

  }

}
