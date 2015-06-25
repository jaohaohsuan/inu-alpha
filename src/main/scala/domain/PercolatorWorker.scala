package domain

import akka.actor.{ActorLogging, ActorRef, Actor}
import akka.contrib.pattern.ClusterClient.SendToAll

import org.elasticsearch.index.query.MatchQueryBuilder
import scala.concurrent.duration._

import com.sksamuel.elastic4s.{BoolQueryDefinition, ElasticClient}

class PercolatorWorker(clusterClient: ActorRef) extends Actor with ActorLogging {

  import StoredQueryPercolatorProtocol._
  import StoredQueryAggregateRoot.{ StoredQuery, BoolClause, MatchBoolClause, SpanNearBoolClause, NamedBoolClause }

  val client = ElasticClient.local

  import context.dispatcher
  val pullingTask = context.system.scheduler.scheduleOnce(10.seconds, clusterClient,
    SendToAll(`/user/stored-query-aggregate-root/active`, Pull))

  def receive: Receive = {
    case Changes(items) =>
      log.info(s"Found ${items.size} changes")

      import com.sksamuel.elastic4s.ElasticDsl._

      items.foreach { case StoredQuery(percolatorId, title, clauses) =>

        val boolQuery = clauses.values.foldLeft(new BoolQueryDefinition)(build(_,_))

        client.execute {
            register id percolatorId into "inu-percolate2" query {
              boolQuery
            }
          }.await
      }
      // reference: https://github.com/sksamuel/elastic4s/blob/57594148b77ebd836d11881ead1783ca78b61db6/elastic4s-core/src/test/scala/com/sksamuel/elastic4s/PercolateTest.scala
    case _ =>

  }

  def init()= {
    import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s.mappings.FieldType._
    client.execute {
      create index "inu-percolate" mappings {
        "stt" as {
          "dialogs" inner (
              "name" typed StringType,
              "content" typed StringType,
              "time" typed IntegerType
            )
        }
      }
    }.await
  }

  def build(bool: BoolQueryDefinition,clause: BoolClause): BoolQueryDefinition = {

    import com.sksamuel.elastic4s._

    val qd: QueryDefinition = clause match {
      case MatchBoolClause(query, operator, _) =>
        new MatchQueryDefinition("dialogs.content", query).operator(MatchQueryBuilder.Operator.valueOf(operator))
      case SpanNearBoolClause(terms, slop, inOrder, _) =>
        terms.foldLeft(slop.map { new SpanNearQueryDefinition().slop }.getOrElse(new SpanNearQueryDefinition())){ (qb, term) =>
          qb.clause(new SpanTermQueryDefinition("dialogs.content", term)) }
          .inOrder(inOrder)
          .collectPayloads(false)
      case NamedBoolClause(_, _, _, clauses) =>
        clauses.values.foldLeft(new BoolQueryDefinition)(build(_, _))
    }
    clause.occurrence match {
      case "must" =>
        bool.must(qd)
      case "must_not" =>
        bool.not(qd)
      case "should" =>
        bool.should(qd)
    }
  }
}
