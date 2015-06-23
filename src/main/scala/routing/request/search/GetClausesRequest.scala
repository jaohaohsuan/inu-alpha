package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll}
import routing.request.PerRequest
import spray.routing._
import spray.http.StatusCodes._
import net.hamnaberg.json.collection.{Item, JsonCollection, Property, Link, Template}
import util.CollectionJsonSupport
import domain.StoredQueryItemsView._


case class QueryStoredQueryItemsRequest(ctx: RequestContext,
                                        clusterClient: ActorRef) extends PerRequest with CollectionJsonSupport {

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.Query(""))

  def processResult: Receive = {
    case QueryResponse(items) =>
      response {
        URI { href =>

          val temporaryLink = Link(href.resolve(s"${href.getPath}/temporary"), "edit")

          complete(OK,
            JsonCollection(href, List(temporaryLink), items.map { e =>
              Item(href.resolve(s"template/${e.id}"), List(Property("name", e.title)), List.empty)
            }))
        }
      }
    case message =>
      response {
        complete(BadRequest, message)
      }
  }
}

case class GetStoredQueryItemRequest(ctx: RequestContext,
                                     clusterClient: ActorRef,
                                     storedQueryId: String) extends PerRequest with CollectionJsonSupport {

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, GetItem(storedQueryId))

  def processResult: Receive = {

    case ItemDetailResponse(item@StoredQueryItem(id, title)) =>
      response {
        URI { href =>
          complete(OK, JsonCollection(
            new java.net.URI(s"$href".substring(0, s"$href".lastIndexOf("/"))),
            links = List.empty,
            List(Item(href, List(Property("title", title)), itemLinks(href.resolve(s"$id/occur")))),
            List.empty, Some(Template(List(Property("title", ""))))
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
      Link(href.resolve("must"),     rel = "section"),
      Link(href.resolve("must_not"), rel = "section"),
      Link(href.resolve("should"),   rel = "section"),
      Link(href.resolve("match"),    rel = "edit"),
      Link(href.resolve("near"),     rel = "edit"),
      Link(href.resolve("named"),    rel = "edit")
    )
}

case class GetStoredQueryClausesRequest(ctx: RequestContext, clusterClient: ActorRef,
                                        storedQueryId: String) extends PerRequest with CollectionJsonSupport {

  import routing.StoredQueryRoute._
  import domain.StoredQueryAggregateRoot._


  def processResult: Receive = {

    case message : GetItemClauses =>
      clusterClient ! SendToAll(storedQueryItemsViewSingleton, message)

    case ItemClausesResponse(clauses) =>
      response {
        URI { href =>

          val items = clauses.map {
            case (id, NamedBoolClause(referredId, title, occurrence, _)) =>
              Item(href.resolve(s"named/$id"), NamedClause(referredId, title, occurrence), links = List.empty)
            case (id, MatchBoolClause(query, operator, occurrence)) =>
              Item(href.resolve(s"match/$id"), MatchClause(query, operator, occurrence), links = List.empty)
            case (id, SpanNearBoolClause(terms, slop, inOrder, occurrence)) =>
              Item(href.resolve(s"near/$id"), SpanNearClause(terms.mkString(" "), slop, inOrder, occurrence), links = List.empty)
          }.toList

          complete(OK, JsonCollection(new java.net.URI(s"$href".substring(0, s"$href".lastIndexOf("/"))), List.empty, items))
        }
      }

    case ItemNotFound =>
      response {
        complete(NotFound)
      }

  }
}
