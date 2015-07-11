package domain

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.pattern._
import com.sksamuel.elastic4s.BoolQueryDefinition
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.indices.IndexMissingException
import util.ElasticSupport
import scala.concurrent.Future
import scala.concurrent.duration._

class PercolatorWorker(clusterClient: ActorRef, node: Option[org.elasticsearch.node.Node]) extends Actor with util.ImplicitActorLogging with ElasticSupport {

  val client = node.map { com.sksamuel.elastic4s.ElasticClient.fromNode }.getOrElse(
    com.sksamuel.elastic4s.ElasticClient.remote("127.0.0.1", 9300)
  )

  import StoredQueryAggregateRoot.{BoolClause, MatchBoolClause, NamedBoolClause, SpanNearBoolClause, StoredQuery}
  import StoredQueryPercolatorProtocol._
  import context.dispatcher


  val pullingTask = context.system.scheduler.schedule(5.seconds, 5.seconds, clusterClient,
    SendToAll(`/user/stored-query-aggregate-root/active`, Pull))

  override def postStop(): Unit = pullingTask.cancel()

  val creatingIndex: Receive = {

    case resp: CreateIndexResponse =>
      resp.logInfo(_.getContext.toString)
      context.unbecome()

    case msg =>
      log.error(s"unable to process the message: $msg")
  }

  val processing: Receive = {
    case Changes(items) =>
      import com.sksamuel.elastic4s.ElasticDsl._
      val f = Future.traverse(items){
        case (StoredQuery(percolatorId, title, clauses, tags), version) =>
          val boolQuery = clauses.values.foldLeft(new BoolQueryDefinition)(assembleBoolQuery)
          client.execute {
            register id percolatorId into percolatorIndex query boolQuery fields
              Map("enabled" -> true, "title" -> title, "tags" -> tags.toArray)
          } map { resp => (percolatorId, version) }
      }

      f onSuccess {
        case changes: Set[(String, Int)] =>
          clusterClient ! SendToAll(`/user/stored-query-aggregate-root/active`, RegisterQueryOK(changes))
      }

      f onFailure {
        case error =>
          rge[IndexMissingException](error) match {
            case Some(_) =>
              context.become(creatingIndex, discardOld = false)
              createPercolatorIndex pipeTo self
            case None =>
              log.error(s"${error.getMessage}, ${error.getClass.getSimpleName}")
              context.stop(self)
          }
      }
  }


  def receive: Receive = processing
}
