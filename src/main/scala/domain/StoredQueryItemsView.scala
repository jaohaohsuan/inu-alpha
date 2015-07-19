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
        tags.map { matchQuery("tags", _).operator("or") }
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

  case class VttRecord(highlights: Seq[(String, String)] = List.empty)

  object VttRecord {
    def apply(highlightFields: Map[String, HighlightField]): VttRecord = {

      val sentence = """((agent|customer)\d{1,2}-\d+)\s(.+)""".r

      val highlights = highlightFields.flatMap { case (_, hf) => hf.getFragments }.flatMap(txt =>
        txt.string match {
          case sentence(cue, value) => Some((cue, value))
          case _ => None
        }).toSeq

      VttRecord(highlights)
    }
  }

  case class PreviewResponse(hits: Set[(String, Seq[String])])

  case class QueryResponse(items: Set[(String, StoredQueryItem)], tags: Set[String])

  case class GetItem(id: String)

  case class GetItemClauses(id: String, occurrence: String)

  case class ItemDetailResponse(id: String, item: StoredQueryItem)

  case class ItemClausesResponse(clauses: Map[Int, BoolClause])

  case class ItemNotFound(id: String)

  val storedQueryItemsViewSingleton = "/user/stored-query-items-view/active"
}

class StoredQueryItemsView(node: Node) extends PersistentView with ImplicitActorLogging {

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
      import elastics.PercolatorIndex._
      implicit val timeout = Timeout(5.seconds)

      client.execute {
          search in `inu-percolate/.percolator` query bool { must { q.asQueryDefinitions }} fields ("title", "tags") size 50
      } map {
        resp => queryResp.copy(items = resp.hits.map { h => h.id -> StoredQueryItem(h) }.toSet)
      } recover { case ex =>
        ex.logError()
        queryResp } pipeTo sender()

    case Preview(storedQueryId) =>
      import com.sksamuel.elastic4s.ElasticDsl._
      implicit val timeout = Timeout(5.seconds)

      val queries = items.get(storedQueryId).map { _.buildBoolQuery() }
                                            .map { case (_, q) => q }
                                            .getOrElse(queryStringQuery(""))

      val f = client.execute {
        (search in "lte*" query queries fields("vtt") highlighting(
          options requireFieldMatch true preTags "<b>" postTags "</b>",
          highlight field "agent*" fragmentSize 50000,
          highlight field "customer*" fragmentSize 50000,
          highlight field "dialogs" fragmentSize 50000)).logInfo()
      }.map { resp =>

        PreviewResponse(resp.hits.map { h =>

        val vtt: Map[String, String] = h.fieldOpt("vtt").map { value =>
          val vttSentence = """(.+-\d+)([\s\S]+)""".r
          value.values().map { e =>
            e.toString match {
              case vttSentence(cueid, content) => cueid -> content
              case _ => e.toString -> "nothing"
            }
          }.toMap
        }.getOrElse(Map.empty[String,String])

        val highlightSentence = """(.+\d+-\d+)\s(.+)""".r

        val sentences = h.highlightFields.flatMap { case (_, hf) => hf.getFragments }.map(txt =>
          txt.string match {
            case highlightSentence(cueid, highlight) =>
              vtt.get(cueid).map { _.replaceAll("""(<v\b[^>]*>)[^<>]*(<\/v>)""", s"$$1$highlight}$$2") }.getOrElse(s"$vtt")
            case _ => txt.string
          }
        )
        s"${h.index}/${h.`type`}/${h.id}" -> sentences.toSeq
      }.toSet) }

      f pipeTo sender
  }
}
