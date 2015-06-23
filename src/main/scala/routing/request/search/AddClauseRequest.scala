package routing.request.search

import akka.actor.{ActorRef, Props}
import akka.contrib.pattern.ClusterClient.{Send, SendToAll}
import routing.request._
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._


case class AddClauseRequest(ctx: RequestContext,
                            clusterClient: ActorRef,
                            storedQueryId: String) extends PerRequest {

  import domain.StoredQueryAggregateRoot._

  def processResult: Receive = {

    case c: BoolClause =>
      clusterClient ! SendToAll(storedQueryAggregateRootSingleton, AddClause(storedQueryId, c))

    case ClauseAddedAck(id)  =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$id")){
            complete(Created)
          }
        }
      }

    case CycleInDirectedGraphError =>
      response {
        complete(NotAcceptable)
      }
  }
}