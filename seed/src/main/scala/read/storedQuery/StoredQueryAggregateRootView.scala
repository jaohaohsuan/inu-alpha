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
import common.StringSetHolder


object StoredQueryAggregateRootView {

  case class StoredQueryData(title: String, tags: Option[String])

  def props(implicit client: Client) = Props(classOf[read.storedQuery.StoredQueryAggregateRootView], client)
}

class StoredQueryAggregateRootView(private implicit val client: org.elasticsearch.client.Client) extends MaterializeView {

  import akka.stream.scaladsl.Sink
  import context.dispatcher
  import elastic.ImplicitConversions._
  import es.indices.storedQuery
  import StoredQueryAggregateRootView._

  var tags = StringSetHolder(Set.empty[String])

  val source = readJournal
    .eventsByPersistenceId(NameOfAggregate.Root)
    .mapConcat(flatten)

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
      occur -> JArray(groupedClauses.map(boolClauseToJObject).toList) :: acc
    }

    val body = pretty(render(JObject(("item", JObject(id, data)), ("occurs", JObject(occurs:_*))) merge percolatorDoc))

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
      source.mapAsync(1){ s => self ? StringSetHolder(s.tags) }.runWith(Sink.ignore)

    case b: IndicesExistsRequestBuilder =>
      b.execute().asFuture pipeTo self

    case StringSetHolder(xs) =>
      tags = tags.append(xs)
      sender ! "ack"
      //log.info(s"tags: $tags")
    case protocol.storedQuery.Exchange.SearchTags =>
      sender() ! tags
    case unknown =>
      log.warning(s"unexpected message catch $unknown")
  }
}
