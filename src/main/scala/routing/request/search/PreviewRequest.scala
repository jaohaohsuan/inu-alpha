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
      case s:StoredQuery if s.clauses.size > 0 => Some(s.buildBoolQuery()._3)
      case Some(s:StoredQuery) if s.clauses.size > 0 => Some(s.buildBoolQuery()._3)
      case unknown =>
        println("empty clause or unknown message received")
        Some(new QueryStringQueryDefinition("nothing:nothing"))
    }
  } catch {
    case ex: Exception => None
  }
}

case class LteCountRequest(ctx: RequestContext,
                           clusterClient: ActorRef, storedQueryId: String) extends PerRequest
with CollectionJsonSupport
with elastics.LteIndices
with util.ImplicitActorLogging {


  import context.dispatcher

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.GetStoredQuery(storedQueryId))

  def processResult: Receive = {
    case StoredQueryResponse(Some(StoredBoolQuery(qry))) => {
      `GET lte*/_count`(qry).onComplete {
        case Success(resp) =>
          response {
            requestUri { uri =>

              val data = List(
                ValueProperty("count",Some(resp.getCount.toInt))
              )

              val item = Item(s"$uri".uri, data, List.empty)
              complete(OK, JsonCollection(s"$uri".replaceFirst("""\/status""", "").uri, List.empty, item))
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

case class PreviewRequest(ctx: RequestContext,
                          clusterClient: ActorRef, storedQueryId: String) extends PerRequest
  with CollectionJsonSupport
  with elastics.LteIndices
  with util.ImplicitActorLogging {

  import com.sksamuel.elastic4s.ElasticDsl._
  import context.dispatcher

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.GetStoredQuery(storedQueryId))

  def processResult: Receive = {
    case StoredQueryResponse(Some(StoredBoolQuery(qry))) => {
      import elastics.LteIndices._
      `GET lte*/_search`(qry).onComplete {
        case Success(resp) =>
          response {
            requestUri { uri =>
              val items = resp.hits.map { case SearchHitHighlightFields(location, fragments) =>

                val `HH:mm:ss.SSS` = org.joda.time.format.DateTimeFormat.forPattern("HH:mm:ss.SSS")
                def startTime(value: VttHighlightFragment): Int =
                  `HH:mm:ss.SSS`.parseDateTime(value.start).getMillisOfDay

                val data = List(
                  ListProperty("highlight", fragments.toList.sortBy{ e => startTime(e) }.map{ case VttHighlightFragment(start, keywords) => s"$start $keywords" }.toSeq ),
                  ValueProperty("keywords", Some(fragments.flatMap { _.keywords.split("""\s""") }.toSet.mkString(" ")))
                )
                val href = s"${uri.withPath(Path(s"/_vtt/$location")).withQuery(("_id", storedQueryId))}".uri
                Item(href, data, List.empty)
              }
              complete(OK, JsonCollection(s"$uri".replaceFirst("""\/preview""", "").uri, List(Link(s"${uri}/status".uri, "status", None)), items.toList))
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
