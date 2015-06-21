package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{SendToAll, Send}
import routing.request.PerRequest
import spray.routing._
import spray.http.StatusCodes._
import net.hamnaberg.json.collection._
import domain.search._
import util.CollectionJsonSupport
import StoredQueryCommandQueryProtocol._

case class GetStoredQueryItemsRequest(ctx: RequestContext,
                                      clusterClient: ActorRef) extends PerRequest with CollectionJsonSupport {

  clusterClient ! SendToAll(storedQueryRepoSingleton, StoredQueryRepo.Query)

  def processResult: Receive = {
    case StoredQueryRepo.QueryResponse(items) =>
      response {
        URI { href =>
          complete(OK,
            JsonCollection(href, List.empty, items.map { e =>
              Item(href.resolve(s"template/${e.storedQueryId}"), List(Property("name", e.name)), List.empty)
            }))

        }
      }
    case message =>
      response {
        complete(InternalServerError, message)
      }
  }
}

case class GetClausesRequest( ctx: RequestContext,
                              clusterClient: ActorRef,
                              storedQueryId: String, occurrence: String) extends PerRequest with CollectionJsonSupport{

  import StoredQueryCommandQueryProtocol._

  clusterClient ! Send(storedQueryViewRegion, StoredQueryQuery(storedQueryId, occurrence), localAffinity = true)

  def processResult: Receive = {
    case StoredQueryResponse(_, clauses) =>

      response {
          URI { href =>
              complete(OK, JsonCollection(href, List.empty, clauses.map {
                case c @ NamedBoolClause(storedQueryId, occurrence, Some(name), _) =>
                  Item(href.resolve(s"named/${c.hashCode}"), routing.StoredQueryRoute.NamedClause(storedQueryId, name, occurrence), List.empty)
                case c @ MatchClause(query, operator, occurrence) =>
                  Item(href.resolve(s"match/${c.hashCode}"), routing.StoredQueryRoute.MatchClause(query, operator, occurrence), List.empty)
                case c @ SpanNearClause(terms, slop, inOrder, occurrence) =>
                  Item(href.resolve(s"match/${c.hashCode}"), routing.StoredQueryRoute.SpanNearClause(terms.mkString(" "), slop, inOrder, occurrence), List.empty)
              }))

          }
      }

  }
}
