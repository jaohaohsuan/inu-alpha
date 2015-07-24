package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll}
import routing.request.PerRequest
import spray.routing._
import spray.http.StatusCodes._
import net.hamnaberg.json.collection._
import util.CollectionJsonSupport
import domain.StoredQueryItemsView._

case class GetStoredQueryItemRequest(ctx: RequestContext,
                                     clusterClient: ActorRef,
                                     storedQueryId: String) extends PerRequest with CollectionJsonSupport {

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, GetItem(storedQueryId))

  def processResult: Receive = {

    case ItemDetailResponse(id, item) =>
      response {
        URI { href =>
          val template = Template(item.copy(status = None))

          complete(OK, JsonCollection(
            s"$href".replaceAll("""/\w*$""", "").uri,
            links = List.empty,
            List(Item(href, item, itemLinks(href.resolve(s"$id/occur")))),
            List.empty, Some(template)
          )
          )
        }
      }

    case ItemNotFound(id) =>
      response {
        complete(NotFound)
      }
  }

  def itemLinks(href: java.net.URI): List[Link] =
    List(
      Link(href.resolve("preview"),  rel = "preview", name = Some("preview")),
      Link(href.resolve("must"),     rel = "section", name = Some("must")),
      Link(href.resolve("must_not"), rel = "section", name = Some("must_not")),
      Link(href.resolve("should"),   rel = "section", name = Some("should")),
      Link(href.resolve("match"),    rel = "edit"),
      Link(href.resolve("near"),     rel = "edit"),
      Link(href.resolve("named"),    rel = "edit")
    )
}


