package read.storedFilter

import akka.actor.Props
import akka.persistence.query.EventEnvelope
import org.elasticsearch.client.Client
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.{QueryBuilder, BoolQueryBuilder, QueryBuilders}
import org.json4s.JValue
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import protocol.storedFilter.{BoolClause, RangeQuery, TermQuery, TermsQuery}
import read.MaterializeView

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
      val zero: (JValue, JValue) =(
        ("must" -> JArray(Nil)) ~
        ("should" -> JArray(Nil)) ~
        ("must_not" -> JArray(Nil)), ("query" ->
        ("bool" ->
          ("must" -> JArray(Nil)) ~
            ("must_not" -> JArray(Nil)) ~
            ("should" -> JArray(Nil)))))
      val (source, query) = entity.clauses.foldLeft(zero){ (acc, e) => e.add(acc) }
      es.indices.storedFilter.update(id, typ, pretty(render(source merge query)))

  } }.runWith(Sink.ignore)

  implicit class Clause0(kv: (String, BoolClause)) {
    val (id, clause) = kv

    def add(acc: (JValue,  JValue)): (JValue, JValue)= {

      val qb = clause match {
        case TermQuery(_, field, value) => "term" -> (field -> value)
        case TermsQuery(_, field, array) => "terms" -> (field -> array)
        case RangeQuery(_, field, gte, lte) => "range" -> (field -> ("gte" -> gte) ~ ("lte" -> lte))
      }

      lazy val elem: JObject = {
        val data = ("field" -> clause.field)
        clause match {
          case TermQuery(occur, field, value) =>
            ("href" -> s"$field/term/$id") ~
              ("data" -> data ~ ("value" -> value) ~ ("query" -> "term"))
          case TermsQuery(occur, field, value) =>
            ("href" -> s"$field/terms/$id") ~
              ("data" -> data ~ ("value" -> value) ~ ("query" -> "terms"))
          case RangeQuery(occur, field, gte, lte) =>
            ("href" -> s"$field/range/$id") ~
              ("data" -> data ~ ("gte" -> gte) ~ ("lte" -> lte) ~("query" -> "range"))
        }
      }

      val (source, query) = acc

      (source.transformField {
        case (clause.occurrence, JArray(xs)) => clause.occurrence -> JArray(elem :: xs)
      },
       query.transformField {
         case ("bool", bool: JObject) => "bool" -> bool.transformField {
           case (clause.occurrence, JArray(xs)) => (clause.occurrence, JArray(qb :: xs))
         }
      })
    }
  }

  def receive: Receive = {
    case _ =>
  }
}
