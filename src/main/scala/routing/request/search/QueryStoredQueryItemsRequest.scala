package routing.request.search


import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll}
import org.elasticsearch.action.search.SearchResponse
import routing.request.PerRequest
import spray.routing._
import spray.http.StatusCodes._
import net.hamnaberg.json.collection._
import util.CollectionJsonSupport
import domain.StoredQueryItemsView._

case class PreviewRequest(ctx: RequestContext,
                          clusterClient: ActorRef, storedQueryId: String) extends PerRequest with CollectionJsonSupport with util.ImplicitActorLogging {

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.Preview(storedQueryId))

  def processResult: Receive = {
    case PreviewResponse(hits) =>
      response {
        URI { href =>
          complete(OK, JsonCollection(
             href, List.empty,
              hits.map {
                case (location,v) =>
                  v.highlights.logInfo()
                  Item(s"$href".replaceAll(""":\d.*""", s":9200/$location").uri, v.highlights.map { case(name,v)=> ListProperty(name,v) }, List.empty[Link])
              }.toList)
          )
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
