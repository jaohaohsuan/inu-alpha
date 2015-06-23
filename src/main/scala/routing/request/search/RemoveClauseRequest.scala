package routing.request.search

import akka.actor.{ReceiveTimeout, ActorRef}
import akka.contrib.pattern.ClusterClient.{SendToAll}
import akka.util.Timeout
import routing.request.PerRequest
import spray.http.StatusCodes._
import spray.routing.RequestContext
import akka.pattern._
import scala.concurrent.duration._

case class RemoveClauseRequest(ctx: RequestContext, clusterClient: ActorRef, storedQueryId: String) extends PerRequest {

  import domain.StoredQueryAggregateRoot._
  import domain.StoredQueryItemsView._
  import context.dispatcher

  def processResult: Receive = {

    case occurrence: String =>
      implicit val timeout = Timeout(5.seconds)
      (clusterClient ? SendToAll(storedQueryItemsViewSingleton, GetItemClauses(storedQueryId, occurrence))).map {
        case ItemClausesResponse(clauses) =>
          RemoveClauses(storedQueryId, clauses.keys.toList)
      }.recover {
        case other =>
          log.error(s"recover message: $other")
          ItemNotFound
      } pipeTo self

    case ItemNotFound =>
      response {
        complete(NotFound)
      }

    case msg: RemoveClauses =>
      clusterClient ! SendToAll(storedQueryAggregateRootSingleton, msg)

    case ClausesRemovedAck =>
      response {
        complete(NoContent)
      }
  }
}
