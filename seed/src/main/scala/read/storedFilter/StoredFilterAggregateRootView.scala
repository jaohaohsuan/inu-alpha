package read.storedFilter

import akka.actor.Props
import akka.persistence.query.EventEnvelope
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilder, QueryBuilders}
import org.json4s.JValue
import org.json4s.JValue
import org.json4s.JsonAST._
import protocol.storedFilter.{BoolClause, TermQuery}
import read.MaterializeView
import elastic.ImplicitConversions._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.concurrent.Future

object StoredFilterAggregateRootView {
  def props(implicit client: Client) = Props(classOf[StoredFilterAggregateRootView], client)
}

class StoredFilterAggregateRootView(private implicit val client: Client) extends MaterializeView {

  import akka.stream.scaladsl.Sink
  import context.dispatcher

  import domain.storedFilter.StoredFilterAggregateRoot._
  import protocol.storedFilter.NameOfAggregate

  def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(id, typ, title) =>
        ("_id" -> id) ~
        ("type" -> typ) ~
        ("source" ->
          ("title" -> title): JObject) :: Nil
      case _ => Nil
    }
  }

  val source = readJournal
    .eventsByPersistenceId(NameOfAggregate.root.name)
    //.mapConcat(flatten)

  source.mapAsync(1) { envelope => envelope.event match {
    case ItemCreated(id, typ, title) =>
      val json = compact(render("title" -> title: JObject))
      es.indices.storedFilter.index(id, typ, json)

    case ItemUpdated(id, typ, entity) =>
      import QueryBuilders._
      val zero: (BoolQueryBuilder, JValue) = (boolQuery(), ("query" -> JObject()) ~ ("must" -> JArray(Nil)) ~ ("should" -> JArray(Nil)) ~ ("must_not" -> JArray(Nil)))
      val (filter, json) =  entity.clauses.foldLeft(zero){ (acc, e) => e.add(acc) }
      //json.logInfo(j => pretty(render(j)))
      es.indices.storedFilter.update(id, typ, pretty(render(json)))

  } }.runWith(Sink.ignore)

  implicit class Clause0(kv: (String, BoolClause)) {

    val (id,clause) = kv

    import QueryBuilders._

    def add(acc: (BoolQueryBuilder, JValue)): (BoolQueryBuilder, JValue) = {

      val (bool, json) = acc

      val qb =  clause match {
        case TermQuery(_, occur, typ, field, value) => build(termQuery(field, _), value)
      }

     lazy val elem: JObject = clause match {
        case TermQuery(_, occur, typ, field, value) =>
          ("href" -> s"$field/term/$id") ~
          ("data" -> ("value" -> value) ~ ("occurrence" -> occur))
      }

      val filter = clause.occurrence match {
        case "must" => bool.must(qb)
        case "should" => bool.should(qb)
        case "must_not" => bool.mustNot(qb)
      }

      (filter, json.transformField {
        case (clause.occurrence, JArray(xs)) => clause.occurrence -> JArray(elem :: xs)
        case ("query", _) => "query" -> parse(s"$filter")
      })
    }

    private def build(f: Any => QueryBuilder, value: JValue) =
      value match {
        case JString(str) => f(str)
        case JLong(long) => f(long)
        case JDouble(double) => f(double)
        case JBool(bool) => f(bool)
      }
    }

  def receive: Receive = {
    case _ =>
  }
}
