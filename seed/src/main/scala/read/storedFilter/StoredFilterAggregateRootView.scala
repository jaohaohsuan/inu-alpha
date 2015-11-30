package read.storedFilter

import akka.actor.Props
import org.elasticsearch.client.Client
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

  val source = readJournal.eventsByPersistenceId(NameOfAggregate.root.name)

  source.mapAsync(1) { envelope => envelope.event match {
    case ItemCreated(id, typ, title) =>
      val json = compact(render("title" -> title: JObject))
      es.indices.storedFilter.index(id, typ, json)

    case ItemUpdated(id, typ, entity) =>
      val zero: (JValue, JValue) = (
        ("title" -> entity.title) ~ ("must" -> JArray(Nil)) ~ ("should" -> JArray(Nil)) ~ ("must_not" -> JArray(Nil)),
        "query" -> ("bool" -> ("must" -> JArray(Nil)) ~ ("must_not" -> JArray(Nil)) ~ ("should" -> JArray(Nil))))
      val (source, query) = entity.clauses.foldLeft(zero){ (acc, e) => e.add(acc) }
      es.indices.storedFilter.update(id, typ, pretty(render(source merge query)))

  } }.runWith(Sink.ignore)

  implicit class Clause0(kv: (String, BoolClause)) {

    val (id, clause) = kv

    lazy val termLevelQuery = clause match {
      case TermQuery(_, field, value) => "term" -> (field -> value)
      case TermsQuery(_, field, array) => "terms" -> (field -> array)
      case RangeQuery(_, field, gte, lte) => "range" -> (field -> ("gte" -> gte) ~ ("lte" -> lte))
    }

    lazy val item = {
      val data = "field" -> clause.field
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

    def add(acc: (JValue,  JValue)): (JValue, JValue)= {
      val (source, query) = acc
      (source.transformField { case (clause.occurrence, JArray(xs)) => clause.occurrence -> JArray(item :: xs) },
       query.transformField {
         case ("bool", bool: JObject) => "bool" -> bool.transformField {
           case (clause.occurrence, JArray(xs)) => (clause.occurrence, JArray(termLevelQuery :: xs))
         }
      })
    }
  }

  def receive: Receive = {
    case _ =>
  }
}
