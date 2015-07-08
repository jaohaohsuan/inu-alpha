package domain

import akka.actor._
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence.PersistentView
import akka.util.Timeout
import util.ImplicitActorLogging

import scala.concurrent.duration._

object StoredQueryItemsView {

  import StoredQueryAggregateRoot.BoolClause

  case class Query(text: Option[String], tags: Option[String])

  case class StoredQueryItem(title: String, tags: Option[String], status: Option[String]) {
    require( title.nonEmpty )
  }

  case class QueryResponse(items: Set[(String, StoredQueryItem)], tags: Set[String])

  case class GetItem(id: String)

  case class GetItemClauses(id: String, occurrence: String)

  case class ItemDetailResponse(id: String, item: StoredQueryItem)

  case class ItemClausesResponse(clauses: Map[Int, BoolClause])

  case class ItemNotFound(id: String)

  val storedQueryItemsViewSingleton = "/user/stored-query-items-view/active"
}

class StoredQueryItemsView extends PersistentView with ImplicitActorLogging {

  import StoredQueryAggregateRoot._
  import StoredQueryItemsView._

  override val viewId: String = "stored-query-aggregate-root-view"

  override val persistenceId: String = "stored-query-aggregate-root"

  var items: Map[String, StoredQuery] = Map(temporaryId -> StoredQuery(temporaryId, "temporary"))
  var queryResp = QueryResponse(Set.empty, Set.empty)

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
      updateState(xs.toSeq: _*)

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


    case Query(queryString, queryTags) =>

      import akka.pattern._
      import com.sksamuel.elastic4s.ElasticDsl._
      import context.dispatcher
      import util.ElasticSupport._

      import scala.collection.JavaConversions._

      implicit val timeout = Timeout(5.seconds)

      val queries = List(
        queryString.map { queryStringQuery(_) asfields "_all" },
        queryTags.map { matchQuery("tags", _) }
      )

      client.execute {
        (search in percolatorIndex -> ".percolator" query bool {
          must {
            queries.flatten
          }
        } fields ("title", "tags") size 10).logInfo()
      }.map { resp =>
        queryResp.copy(items = resp.logInfo(_.toString).hits.map { hit => hit.id ->
          StoredQueryItem(hit.field("title").value[String],
            hit.fieldOpt("tags").map { _.values().mkString(" ") },
            Some("enabled")) }.toSet ) }
        .recover {
        case ex =>
          ex.logError()
          queryResp
      } pipeTo sender()
  }
}
