package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.SendToAll
import domain.StoredQueryItemsView._
import net.hamnaberg.json.collection._
import routing.request.PerRequest
import spray.http.StatusCodes._
import spray.http.Uri.Path
import spray.routing._
import util.CollectionJsonSupport

import scala.util.{Failure, Success}

object StoredBoolQuery {
  import com.sksamuel.elastic4s.{QueryDefinition, QueryStringQueryDefinition}
  import domain.StoredQueryAggregateRoot.StoredQuery
  def unapply(value: AnyRef): Option[QueryDefinition] = try {
    value match {
      case s:StoredQuery => Some(s.buildBoolQuery()._3)
      case Some(s:StoredQuery) => Some(s.buildBoolQuery()._3)
      case unknown =>
        println(s"$unknown")
        Some(new QueryStringQueryDefinition(""))
    }
  } catch {
    case ex: Exception => None
  }
}

case class PreviewRequest(ctx: RequestContext,
                          clusterClient: ActorRef, storedQueryId: String) extends PerRequest
  with CollectionJsonSupport
  with elastics.LteIndices
  with util.ImplicitActorLogging {

  import com.sksamuel.elastic4s.ElasticDsl._
  import context.dispatcher

  val client = com.sksamuel.elastic4s.ElasticClient.remote("127.0.0.1", 9300)

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.GetStoredQuery(storedQueryId))

  def processResult: Receive = {
    case StoredQueryResponse(Some(StoredBoolQuery(qry))) => {
      import elastics.LteIndices._
      `GET lte*/_search`(qry).onComplete {
        case Success(resp) =>
          response {
            requestUri { uri =>
              val items = resp.hits.map { case SearchHitHighlightFields(location, fragments) =>
                val data = List(
                  ListProperty("highlight", fragments.map { case VttHighlightFragment(start, keywords) => s"$start $keywords" }.toSeq ),
                  ValueProperty("keywords", Some(fragments.flatMap { _.keywords.split("""\s""") }.toSet.mkString(" ")))
                )
                val href = s"${uri.withPath(Path(s"/_vtt/$location")).withQuery(("_id", storedQueryId))}".uri
                Item(href, data, List.empty)
              }
              complete(OK, JsonCollection(s"$uri".uri, List.empty, items.toList))
            }
          }
        case Failure(ex) =>
          response {
            complete(InternalServerError)
          }
      }
    }
  }
}
