package domain

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.pattern._
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.indices.IndexMissingException
import scala.concurrent.duration._
import scala.concurrent.{ Future }

import com.sksamuel.elastic4s.{BoolQueryDefinition}

class PercolatorWorker(clusterClient: ActorRef) extends Actor with ActorLogging {

  import util.ElasticSupport._

  import StoredQueryAggregateRoot.{BoolClause, MatchBoolClause, NamedBoolClause, SpanNearBoolClause, StoredQuery}
  import StoredQueryPercolatorProtocol._
  import context.dispatcher

  val percolatorIndex = "inu-percolate"

  val pullingTask = context.system.scheduler.schedule(5.seconds, 5.seconds, clusterClient,
    SendToAll(`/user/stored-query-aggregate-root/active`, Pull))

 override def postStop(): Unit = pullingTask.cancel()

  val creatingIndex: Receive = {

    case resp: CreateIndexResponse =>
      context.unbecome
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
              Map("enabled" -> true,
                  "title" -> title,
                  "tags" -> tags.toArray
              )
          } map { resp => (percolatorId, version) }
      }

      f onSuccess {
        case changes: Set[(String, Int)] =>
          clusterClient ! SendToAll(`/user/stored-query-aggregate-root/active`, RegisterQueryOK(changes))
      }
      f onFailure {
          case ex: IndexMissingException =>
            context.become(creatingIndex, discardOld = false)
            createPercolatorIndex pipeTo self
          case e: Exception =>
            log.error(s"$e")
            context.stop(self)
      }
  }

  def receive: Receive = processing

  def createPercolatorIndex = {

    import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s.mappings.FieldType._

    client.execute {
      create index percolatorIndex mappings (
        mapping name ".percolator" templates (
          template name "template_1" matching "query" matchMappingType "string" mapping {
            field typed StringType
          }
          ),
        ".percolator" as (
          "query" typed ObjectType enabled true,
          "enabled" typed BooleanType index "not_analyzed" includeInAll false,
          "tags" typed StringType index "not_analyzed" includeInAll false
          )
        ,
        "stt" as Seq (
          "dialogs" inner (
            "name" typed StringType index "not_analyzed",
            "content" typed StringType,
            "time" typed IntegerType index "not_analyzed"
            )
        ))
    }
  }

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
