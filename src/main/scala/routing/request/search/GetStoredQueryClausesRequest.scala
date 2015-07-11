package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll}
import routing.request.PerRequest
import spray.routing._
import spray.http.StatusCodes._
import net.hamnaberg.json.collection._
import util.CollectionJsonSupport
import domain.StoredQueryItemsView._

case class GetStoredQueryClausesRequest(ctx: RequestContext, clusterClient: ActorRef,
                                        storedQueryId: String) extends PerRequest with CollectionJsonSupport {

  import domain.StoredQueryAggregateRoot._
  import routing.StoredQueryRoute._


  def processResult: Receive = {

    case message : GetItemClauses =>
      clusterClient ! SendToAll(storedQueryItemsViewSingleton, message)

    case ItemClausesResponse(clauses) =>
      response {
        URI { href =>

          val items = clauses.map {
            case (id, NamedBoolClause(referredId, title, occurrence, _)) =>
              Item(href.resolve(s"named/$id"), NamedClause(referredId, title, occurrence), links = List.empty)
            case (id, MatchBoolClause(query, fields, operator, occurrence)) =>
              Item(href.resolve(s"match/$id"), MatchClause(query, fields, operator, occurrence), links = List.empty)
            case (id, SpanNearBoolClause(terms, fields, slop, inOrder, occurrence)) =>
              Item(href.resolve(s"near/$id"), SpanNearClause(terms.mkString(" "), fields, slop, inOrder, occurrence), links = List.empty)
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
