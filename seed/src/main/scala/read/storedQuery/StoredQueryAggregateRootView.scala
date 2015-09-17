package read.storedQuery

import akka.persistence.query.{EventEnvelope, EventsByPersistenceId}
import domain.storedQuery.StoredQueryAggregateRoot.{ItemCreated, ItemsChanged}
import org.json4s.JObject
import org.json4s.JsonAST._
import org.json4s.native.JsonMethods._
import protocol.storedQuery.{AggregateRoot, StoredQuery}
import read.MaterializeView

import scala.language.implicitConversions

case class SimpleStoredQuery(title: String, tags: Option[String])

class StoredQueryAggregateRootView extends MaterializeView {

  val source = readJournal
    .query(EventsByPersistenceId(AggregateRoot.Name))
    .mapConcat(flatten)
    .map(convertToReadSideType)

  def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(entity, _) => entity :: Nil
      case ItemsChanged(items, _, _) => items.map { case (_, obj) => obj }.toList
      case _ => Nil
    }
  }

  def convertToReadSideType(value: StoredQuery) = {

    import ImplicitJsonConversions._

    implicit def setToOptionString(value : Set[String]): Option[String] = Option(value.mkString(" ")).filter(_.trim.nonEmpty)

    // extracting
    val BoolQueryConversion(boolQuery, searchableFields) = value
    val StoredQuery(storedQueryId, title , clauses, tags) = value

    val prefixPath = s"/_query/template/$storedQueryId"

    val data = JField("data", SimpleStoredQuery(title, tags: Option[String]))
    val href = JField("href", JString(prefixPath))
    val version = JField("version", JString("1.0"))
    val items = JField("items", JArray(List(JObject(data, href))))
    val template = JField("template", JObject(data))

    val fields = clauses.groupBy{ case (_, c) => c.occurrence }.foldLeft(List(items, template, version, href)){ (acc: List[JField], e) =>
      val (occur, groupedClauses) = e
      occur -> JObject(
        ("href", JString(s"$prefixPath/$occur")),
        ("items", JArray(groupedClauses.map { case (clauseId, clause) =>
          JObject(
            ("data", clause),
            ("href", JString(s"$prefixPath/$clauseId"))
          )}.toList))
      ) :: acc
    }

    storedQueryId -> compact(render(
      JObject(
        JField("query", parse(boolQuery.builder.toString)),
        JField("collection", JObject(fields:_*))
      ) merge searchableFields
    ))
  }

  def receive: Receive = {
    case "GO" =>
      import StoredQueryIndex._
      import context.dispatcher
      source
        .mapAsync(1)(saveOrUpdate)
        .runForeach(f => println(f))
      //.runWith(Sink.ignore)
  }
}
