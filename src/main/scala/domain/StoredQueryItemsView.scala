package domain

import akka.actor._
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence.PersistentView
import akka.util.Timeout
import org.elasticsearch.node.Node
import org.elasticsearch.search.highlight.HighlightField
import util.{ElasticSupport, ImplicitActorLogging}
import scala.collection.JavaConversions._
import scala.concurrent.duration._

object StoredQueryItemsView {

  import StoredQueryAggregateRoot.BoolClause

  case class Query(text: Option[String], tags: Option[String]) {
    def asQueryDefinitions = {
      import com.sksamuel.elastic4s.ElasticDsl._
      List(
        text.map { queryStringQuery(_) asfields "_all" },
        tags.map { matchQuery("tags", _) }
      ).flatten
    }
  }

  case class Preview(id: String)

  case class StoredQueryItem(title: String, tags: Option[String], status: Option[String]) {
    require( title.nonEmpty )
  }

  object StoredQueryItem {
    def apply(hit : com.sksamuel.elastic4s.RichSearchHit): StoredQueryItem = {
      StoredQueryItem(hit.field("title").value[String],
        hit.fieldOpt("tags").map { _.values().mkString(" ") },
        Some("enabled"))
    }
  }

  case class SttRecord(highlights: List[(String, Seq[String])] = List.empty)

  object SttRecord {
    def apply(highlightFields: Map[String, HighlightField]): SttRecord = {
      SttRecord(highlightFields.map { case (k,v) => k -> v.getFragments().map { _.toString }.toSeq }.toList)
    }
  }

  case class PreviewResponse(hits: Set[(String, SttRecord)])

  case class QueryResponse(items: Set[(String, StoredQueryItem)], tags: Set[String])

  case class GetItem(id: String)

  case class GetItemClauses(id: String, occurrence: String)

  case class ItemDetailResponse(id: String, item: StoredQueryItem)

  case class ItemClausesResponse(clauses: Map[Int, BoolClause])

  case class ItemNotFound(id: String)

  val storedQueryItemsViewSingleton = "/user/stored-query-items-view/active"
}

class StoredQueryItemsView(node: Node) extends PersistentView with ImplicitActorLogging with ElasticSupport {

  val client = com.sksamuel.elastic4s.ElasticClient.fromNode(node)

  import StoredQueryAggregateRoot._
  import StoredQueryItemsView._

  import akka.pattern._

  override val viewId: String = "stored-query-aggregate-root-view"

  override val persistenceId: String = "stored-query-aggregate-root"

  var items: Map[String, StoredQuery] = Map(temporaryId -> StoredQuery(temporaryId, "temporary"))
  var queryResp = QueryResponse(Set.empty, Set.empty)

  import context.dispatcher

  ClusterReceptionistExtension(context.system).registerService(self)

  def accumulateItemsTags = {
    items.values.map { _.tags }.foldLeft(Set.empty[String]){ _ ++ _ }
  }

  private def updateState(values: (String, StoredQuery)*) = {
    items = items ++ values
    queryResp = queryResp.copy(tags = accumulateItemsTags )
  }

  def receive: Receive = {
    case ItemCreated(entity, dp) if isPersistent =>
      updateState (entity.id -> entity)

    case ItemsChanged(xs, _ , _) =>
      updateState(xs: _*)

    case GetItem(id) =>
      sender ! (for {
        StoredQuery(_, title, _, tags) <- items.get(id)
        item = StoredQueryItem(title, Some(tags.mkString(" ")), Some("enabled"))
      } yield ItemDetailResponse(id, item)).getOrElse(ItemNotFound(id))

    case GetItemClauses(id, occurrence) =>
      sender ! (for {
        StoredQuery(id, _, clauses, _) <- items.get(id)
        filtered = clauses.filter { case (_, clause) => clause.occurrence == occurrence }
      } yield ItemClausesResponse(filtered)).getOrElse(ItemNotFound(id))

    case ChangesRegistered(records) =>
      log.info(s"$records were registered.")

    case q: Query =>
      import com.sksamuel.elastic4s.ElasticDsl._


      implicit val timeout = Timeout(5.seconds)

      client.execute {
          search in percolatorIndex -> ".percolator" query bool { must { q.asQueryDefinitions }} fields ("title", "tags") size 50
      } map {
        resp => queryResp.copy(items = resp.hits.map { h => h.id -> StoredQueryItem(h) }.toSet)
      } recover { case ex =>
        ex.logError()
        queryResp } pipeTo sender()

    case Preview(storedQueryId) =>
      import com.sksamuel.elastic4s.ElasticDsl._
      import com.sksamuel.elastic4s.BoolQueryDefinition

      implicit val timeout = Timeout(5.seconds)

      val queries = items.get(storedQueryId).map { _.clauses.values.foldLeft(new BoolQueryDefinition)(assembleBoolQuery) }
        .getOrElse(queryStringQuery(""))

      val f = client.execute {
        (search in "lte-2015.07.11" types "logs" query queries highlighting(
          options requireFieldMatch true preTags "<b>" postTags "</b>",
          highlight field "r0",
          highlight field "r1",
          highlight field "dialogs")).logInfo()
      }.map { resp => PreviewResponse(resp.hits.map { h => s"${h.index}/${h.`type`}/${h.id}" -> SttRecord(h.highlightFields)}.toSet) }

      f pipeTo sender
  }
}
