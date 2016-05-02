/*
package read.storedQuery

import akka.actor.Props
import akka.pattern._
import akka.persistence.query.EventEnvelope
import akka.util.Timeout
import domain.storedQuery.StoredQueryAggregateRoot.{ItemCreated, ItemsChanged}
import org.elasticsearch.action.admin.indices.exists.indices.{IndicesExistsRequestBuilder, IndicesExistsResponse}
import org.elasticsearch.client.Client
import org.json4s.JObject
import org.json4s.JsonAST._
import org.json4s.native.JsonMethods._
import protocol.storedQuery.{NameOfAggregate, ImplicitJsonConversions, NamedBoolClause, StoredQuery}
import read.MaterializeView
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.language.implicitConversions
import common.StringMapHolder

object StoredQueryAggregateRootView {

  case class StoredQueryData(title: String, tags: Option[String])

  def props(implicit client: Client) = Props(classOf[StoredQueryAggregateRootView], client)
}

class StoredQueryAggregateRootView(private implicit val client: org.elasticsearch.client.Client) extends MaterializeView {

  import akka.stream.scaladsl.Sink
  import elastic.ImplicitConversions._
  import es.indices.storedQuery
  import StoredQueryAggregateRootView._

  var tags = StringMapHolder(Map.empty)

  val source = readJournal
    .eventsByPersistenceId(NameOfAggregate.root.name, 0, Long.MaxValue)
    .mapConcat(flatten)

  def flatten(envelope: EventEnvelope) = {
    envelope.event match {
      case ItemCreated(entity, _) => entity :: Nil
      case ItemsChanged(items, changes, _) =>
        val (clausesRetrievedItems, _) = changes.foldLeft((List.empty[StoredQuery], items.toMap)) { (acc, changedItemId) =>
          val (result, sources) = acc
          sources.get(changedItemId) match {
            case None => acc
            case Some(entity) =>
              val clausesRetrievedItem = retrieveDependencies(entity, sources)
              (clausesRetrievedItem :: result, sources + (changedItemId -> clausesRetrievedItem))
          }}
        clausesRetrievedItems
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

    implicit def setToOptionString(value : Set[String]): Option[String] = Option(value.mkString(" ").trim)

    val BoolQueryConversion(_, percolatorDoc) = value
    val StoredQuery(storedQueryId, title , clauses, tags) = value

    val id = JField("id", JString(storedQueryId))
    val data = JField("data", StoredQueryData(title, tags: Option[String]))

    val occurs: List[(String, JValue)] = clauses.groupBy{ case (_, c) => c.occurrence }.foldLeft(List.empty[JField]){ (acc, e) =>
      val (occur, groupedClauses) = e
      occur -> JArray(groupedClauses.map(boolClauseCollectionItem).toList) :: acc
    }

    val body = compact(render(percolatorDoc merge JObject(("item", JObject(id, data)), ("occurs", JObject(occurs:_*)))))

    storedQueryId -> body
  }

  val checkingTask = context.system.scheduler.schedule(2.seconds, 5.seconds, self, storedQuery.exists)

  def receive: Receive = {
    case r: IndicesExistsResponse if r.isExists =>
      checkingTask.cancel()
      source.map(convertToReadSideType)
                .mapAsync(1){ case (storedQueryId, doc) => storedQuery.save(storedQueryId, doc) }
                //.runForeach(f => println(f))
                .runWith(Sink.ignore)
      implicit val timeout = Timeout(5 seconds)
      source.mapAsync(1){ s => self ? StringMapHolder(Map((s.id, s.tags))) }.runWith(Sink.ignore)

    case builder: IndicesExistsRequestBuilder =>
      builder.execute().future pipeTo self

    case StringMapHolder(xs) =>
      tags = tags.append(xs)
      sender ! "ack"
    case protocol.storedQuery.Exchange.SearchTags =>
      sender() ! tags
    case unknown =>
      log.warning(s"unexpected message catch $unknown")
  }
}
*/