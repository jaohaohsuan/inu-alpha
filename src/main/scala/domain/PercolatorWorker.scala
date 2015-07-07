package domain

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.pattern._
import com.sksamuel.elastic4s.BoolQueryDefinition
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.indices.IndexMissingException
import scala.concurrent.Future
import scala.concurrent.duration._

class PercolatorWorker(clusterClient: ActorRef) extends Actor with util.ImplicitActorLogging {

  import StoredQueryAggregateRoot.{BoolClause, MatchBoolClause, NamedBoolClause, SpanNearBoolClause, StoredQuery}
  import StoredQueryPercolatorProtocol._
  import context.dispatcher
  import util.ElasticSupport._

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
          val boolQuery = clauses.values.foldLeft(new BoolQueryDefinition)(build)
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

  def build(bool: BoolQueryDefinition,clause: BoolClause): BoolQueryDefinition = {

    import com.sksamuel.elastic4s._

    val qd: QueryDefinition = clause match {
      case MatchBoolClause(query, operator, _) =>
        new MatchQueryDefinition("dialogs.content", query).operator(operator.toUpperCase)

      case SpanNearBoolClause(terms, slop, inOrder, _) =>
        val spanNear = new SpanNearQueryDefinition()
        terms.foldLeft(slop.map { spanNear.slop }.getOrElse(spanNear)){ (qb, term) =>
          qb.clause(new SpanTermQueryDefinition("dialogs.content", term)) }
          .inOrder(inOrder)
          .collectPayloads(false)

      case NamedBoolClause(_, _, _, clauses) =>
        clauses.values.foldLeft(new BoolQueryDefinition)(build)
    }

    clause.occurrence match {
      case "must" => bool.must(qd)
      case "must_not" => bool.not(qd)
      case "should" => bool.should(qd)
    }
  }
}
