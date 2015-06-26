package domain

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ClusterClient.SendToAll
import akka.pattern._
import org.elasticsearch.index.percolator.PercolatorException

import scala.concurrent._
import scala.concurrent.duration._
//import scala.concurrent.ExecutionContext.Implicits.global

import com.sksamuel.elastic4s.{BoolQueryDefinition, ElasticClient}

class PercolatorWorker(clusterClient: ActorRef) extends Actor with ActorLogging {

  import StoredQueryAggregateRoot.{BoolClause, MatchBoolClause, NamedBoolClause, SpanNearBoolClause, StoredQuery}
  import StoredQueryPercolatorProtocol._
  import context.dispatcher
  val pullingTask = context.system.scheduler.schedule(0.seconds, 5.seconds, clusterClient,
    SendToAll(`/user/stored-query-aggregate-root/active`, Pull))

  override def postStop(): Unit = pullingTask.cancel()

  def receive: Receive = {
    case Changes(items) => {
      log.info(s"Found ${items.size} changes")

      import com.sksamuel.elastic4s.ElasticDsl._

      val client = ElasticClient.local

      Future.traverse(items){ case (StoredQuery(percolatorId, title, clauses), version) => {
        val boolQuery = clauses.values.foldLeft(new BoolQueryDefinition)(build(_,_))
        log.info("boolQuery created")
        client.execute {
          register id percolatorId into "inu-percolate" query {
            boolQuery
          }
        }
      } } map { case result =>
        log.info(s"$result")
        RegisterQueryOK(items.map { case (entity, version)=> (entity.id, version) })
      } recover {
        case e: PercolatorException =>
          log.info(s"${e.getMessage}")
          buildMapping(client).onComplete { case _ => RegisterQueryNotOK }
        case message => {
        log.info(s"$message")
        RegisterQueryNotOK
      }} pipeTo sender()
    }

      // reference: https://github.com/sksamuel/elastic4s/blob/57594148b77ebd836d11881ead1783ca78b61db6/elastic4s-core/src/test/scala/com/sksamuel/elastic4s/PercolateTest.scala
    case _ =>

  }

  def buildMapping(client: ElasticClient) = {

    import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s.mappings.FieldType._

    client.execute {
      put mapping "inu-percolate" / "stt" as Seq (

        "dialogs" inner (
          "name" typed StringType,
          "content" typed StringType,
          "time" typed IntegerType
          )
      ) ignoreConflicts true

    }
  }

  def build(bool: BoolQueryDefinition,clause: BoolClause): BoolQueryDefinition = {

    //import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s._

    val qd: QueryDefinition = clause match {
      case MatchBoolClause(query, operator, _) =>
        new MatchQueryDefinition("dialogs.content", query).operator(operator.toUpperCase)
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
