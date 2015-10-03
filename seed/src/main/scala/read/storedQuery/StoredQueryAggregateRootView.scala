package read.storedQuery

import akka.persistence.query.{EventEnvelope}
import domain.storedQuery.StoredQueryAggregateRoot.{ItemCreated, ItemsChanged}
import org.json4s.JObject
import org.json4s.JsonAST._
import org.json4s.native.JsonMethods._
import protocol.storedQuery.{AggregateRoot, ImplicitJsonConversions, NamedBoolClause, StoredQuery}
import read.MaterializeView

import scala.language.implicitConversions

case class StoredQueryData(title: String, tags: Option[String])

class StoredQueryAggregateRootView(private implicit val client: org.elasticsearch.client.Client) extends MaterializeView {

  val source = readJournal
    .eventsByPersistenceId(AggregateRoot.Name)
    .mapConcat(flatten)
    .map(convertToReadSideType)

  def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(entity, _) => entity :: Nil
      case ItemsChanged(items, changes, _) => items.map(_._2).toList
        /*val (entities, _) = changes.foldLeft((List.empty[StoredQuery], items.toMap)) { (acc, e) =>
          val (result, sources) = acc
          sources.get(e) match {
            case None => acc
            case Some(s) =>
              (s :: result, sources + (e -> retrieveDependencies(s, sources)))
          }}
        entities*/
      case _ => Nil
    }
  }

  def retrieveDependencies(item: StoredQuery, items: Map[String, StoredQuery]): StoredQuery =
    item.clauses.foldLeft(item) { (acc, e) =>
      e match {
        case (clauseId, n: NamedBoolClause) =>
          val innerItem = items(n.storedQueryId)
          acc.copy(clauses = acc.clauses + (clauseId -> n.copy(clauses = retrieveDependencies(innerItem,items).clauses)))
        case _ => acc
      }
    }

  def convertToReadSideType(value: StoredQuery) = {

    import ImplicitJsonConversions._

    implicit def setToOptionString(value : Set[String]): Option[String] = Option(value.mkString(" ")).filter(_.trim.nonEmpty)

    val BoolQueryConversion(_, percolatorDoc) = value
    val StoredQuery(storedQueryId, title , clauses, tags) = value

    val id = JField("id", JString(storedQueryId))
    val data = JField("data", StoredQueryData(title, tags: Option[String]))

    val occurs = clauses.groupBy{ case (_, c) => c.occurrence }.foldLeft(List.empty[JField]){ (acc, e) =>
      val (occur, groupedClauses) = e
      occur -> JArray(groupedClauses.map { case (clauseId, clause) => JObject(("data", boolClauseToJValue(clause)), ("id", JString(s"$clauseId")))}.toList) :: acc
    }

    storedQueryId -> pretty(render(JObject(("item", JObject(id, data)), ("occurs", JObject(occurs:_*))) merge percolatorDoc))
  }

  def receive: Receive = {
    case "GO" =>
      import es.indices.storedQuery
      import akka.stream.scaladsl.Sink
      import scala.util.Success
      import elastic.ImplicitConversions._
      import context.dispatcher

      storedQuery.exists.asFuture.onComplete {
        case Success(x) if x.isExists() =>
          source
            .mapAsync(1){ case (storedQueryId, doc) => storedQuery.save(storedQueryId, doc) }
            //.runForeach(f => println(f))
            .runWith(Sink.ignore)
        case _ =>
          log.warning(s"${storedQuery.index} doesn't exist then terminate StoredQueryAggregateRootView")
          context.stop(self)
      }
  }
}
