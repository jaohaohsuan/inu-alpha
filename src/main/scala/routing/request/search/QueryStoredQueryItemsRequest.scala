package routing.request.search


import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.SendToAll
import domain.StoredQueryItemsView._
import net.hamnaberg.json.collection._
import routing.request.PerRequest
import spray.http.StatusCodes._
import spray.routing._
import util.CollectionJsonSupport

import scala.util.{Failure, Success}

object StoredBoolQuery {
  import com.sksamuel.elastic4s.{QueryDefinition, QueryStringQueryDefinition}
  import domain.StoredQueryAggregateRoot.StoredQuery
  def unapply(value: AnyRef): Option[QueryDefinition] = try {
    value match {
      case s:StoredQuery => Some(s.buildBoolQuery()._2)
      case Some(s:StoredQuery) => Some(s.buildBoolQuery()._2)
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

  val client = com.sksamuel.elastic4s.ElasticClient.remote("127.0.0.1", 9300)

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.GetStoredQuery(storedQueryId))

  def processResult: Receive = {
    case StoredQueryResponse(Some(StoredBoolQuery(qry))) => {
      import context.dispatcher
      import elastics.LteIndices._
      `GET lte*/_search`(qry).onComplete {
        case Success(resp) =>
          response {
            URI { href =>
              import com.sksamuel.elastic4s.ElasticDsl._
              val items = resp.hits.map { case LteHighlightFields(location, fragments) =>
                val data = List(ListProperty("highlight", fragments.map { _._2 }.toSeq))
                Item(s"$href".replaceAll(""":\d.*""", s":9200/$location").uri, data, List.empty)
              }
              complete(OK, JsonCollection(href, List.empty, items.toList))
            }
          }
        case Failure(ex) =>
      }

    }
  }
}

case class QueryStoredQueryItemsRequest(ctx: RequestContext,
                                        clusterClient: ActorRef, queryString: Option[String], queryTags: Option[String]) extends PerRequest with CollectionJsonSupport {

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.Query(queryString, queryTags))

  def processResult: Receive = {
    case QueryResponse(hits, tags) =>
      response {
        URI { href =>
          """.*_query/template""".r.findFirstIn(s"$href") match {
            case Some(baseUri) =>
              val temporaryLink = Link(s"$baseUri/temporary".uri, "edit")
              val uriSearch = net.hamnaberg.json.collection.Query(s"$baseUri/search".uri,
                rel = "search",
                data = List(ValueProperty("q", Some("search title or any terms"), None),
                  ValueProperty("tags", Some(tags.mkString(" ")), None)
                ))

              val items = hits.map { case (key, value) =>
                Item(s"$baseUri/$key".uri, value, List.empty)
              }.toList

              val template = Some(Template(StoredQueryItem("sample", tags = None, status = None)))
              complete(OK, JsonCollection(baseUri.uri, List(temporaryLink),items ,List(uriSearch), template))
            case None =>
              complete(InternalServerError)
          }
        }
      }
    case message =>
      response {
        complete(BadRequest, message)
      }
  }
}
