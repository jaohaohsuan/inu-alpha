package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.SendToAll
import domain.StoredQueryItemsView._
import net.hamnaberg.json.collection._
import routing.request.PerRequest
import spray.http.StatusCodes._
import spray.routing._
import util.CollectionJsonSupport

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
