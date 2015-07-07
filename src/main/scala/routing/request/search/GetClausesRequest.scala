package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll}
import net.hamnaberg.json.collection.Query
import routing.request.PerRequest
import spray.routing._
import spray.http.StatusCodes._
import net.hamnaberg.json.collection._
import util.CollectionJsonSupport
import domain.StoredQueryItemsView._


case class QueryStoredQueryItemsRequest(ctx: RequestContext,
                                        clusterClient: ActorRef, queryString: Option[String], queryTags: Option[String]) extends PerRequest with CollectionJsonSupport {

  clusterClient ! SendToAll(storedQueryItemsViewSingleton, domain.StoredQueryItemsView.Query(queryString, queryTags))

  def processResult: Receive = {
    case QueryResponse(hits, tags) =>
      response {
        URI { href =>
          val baseUri = s"${href.getScheme}://${href.getHost}:${href.getPort}/_query/template"

          val temporaryLink = Link(java.net.URI.create(s"$baseUri/temporary"), "edit")
          val uriSearch = net.hamnaberg.json.collection.Query(java.net.URI.create(s"$baseUri/search"),
                             rel = "search",
                             data = List(ValueProperty("q", Some("search title or any terms"), None),
                                         ValueProperty("tags", Some(tags.mkString(" ")), None)
                             ))

          val items = hits.map { case (key, value) =>
            Item(java.net.URI.create(s"$baseUri/$key"), value, List.empty)
          }.toList

          complete(OK,
            JsonCollection(java.net.URI.create(baseUri), List(temporaryLink),items ,queries = List(uriSearch)))
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

    case ItemDetailResponse(id, item) =>
      response {
        URI { href =>

          val template = Template(item.copy(status = None))

          complete(OK, JsonCollection(
            new java.net.URI(s"$href".substring(0, s"$href".lastIndexOf("/"))),
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
      Link(href.resolve("must"),     rel = "section", name = Some("must")),
      Link(href.resolve("must_not"), rel = "section", name = Some("must_not")),
      Link(href.resolve("should"),   rel = "section", name = Some("should")),
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
