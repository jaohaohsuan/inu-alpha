package domain

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.pattern._
import com.sksamuel.elastic4s.BoolQueryDefinition
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.indices.IndexMissingException
import org.elasticsearch.transport.RemoteTransportException
import util.ElasticSupport
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

class PercolatorWorker(clusterClient: ActorRef, node: Option[org.elasticsearch.node.Node]) extends Actor
  with util.ImplicitActorLogging {

  val client = node.map { com.sksamuel.elastic4s.ElasticClient.fromNode }.getOrElse(
    com.sksamuel.elastic4s.ElasticClient.remote("127.0.0.1", 9300)
  )

  import StoredQueryAggregateRoot.{BoolClause, MatchBoolClause, NamedBoolClause, SpanNearBoolClause, StoredQuery}
  import StoredQueryPercolatorProtocol._
  import context.dispatcher


  val pullingTask = context.system.scheduler.schedule(5.seconds, 5.seconds, clusterClient,
    SendToAll(`/user/stored-query-aggregate-root/active`, Pull))

  override def postStop(): Unit = pullingTask.cancel()

  val processing: Receive = {
    case Changes(items) =>
      import com.sksamuel.elastic4s.ElasticDsl._
      import elastics.PercolatorIndex._

      val f = Future.traverse(items){
        case (e @ StoredQuery(percolatorId, title, clauses, tags), version) =>
          val (referredClauses, boolQuery) = e.buildBoolQuery()
          client.execute {
            (register id percolatorId into `inu-percolate` query boolQuery fields
              Map("enabled" -> true, "title" -> title, "tags" -> tags.toArray, "referredClauses" -> referredClauses.toArray)).logInfo(_.build.toString)
          } map { resp => (percolatorId, version) }
      }

      f onSuccess {
        case changes: Set[(String, Int)] =>
          clusterClient ! SendToAll(`/user/stored-query-aggregate-root/active`, RegisterQueryOK(changes))
      }

      f onFailure {
        case error =>
          recursive[IndexMissingException](error) match {
            case Some(_) =>
              log.error(s"IndexMissingException: ${error.getMessage}")
            case None =>
              log.error(s"${error.getMessage}, ${error.getClass.getSimpleName}")
              context.stop(self)
          }
      }
  }

  private def recursive[A<: Throwable: ClassTag](exception: Throwable): Option[A] = {
    exception match {
      case e: RemoteTransportException => recursive(e.getCause)
      case e: A => Some(e)
      case _ => None
    }
  }


  def receive: Receive = processing
}
