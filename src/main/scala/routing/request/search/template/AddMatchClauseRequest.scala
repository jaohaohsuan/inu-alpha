package routing.request.search.template


import akka.actor.{Terminated, ActorRef}
import akka.contrib.pattern.ClusterClient.{SendToAll, Send}
import akka.contrib.pattern.ClusterSharding
import domain.search.template.Graph.{Routes, Inconsistency, Get}
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._
import domain.search.template._
import CommandQueryProtocol._

case class AddMatchClauseRequest(ctx: RequestContext, clusterClient: ActorRef,
                                 templateId: String,
                                 query: String,
                                 operator: String,
                                 occur: String) extends PerRequest with AutoGathering {

  clusterClient ! Send(templateRegion, AddClauseCommand(templateId, MatchClause(query, operator, occur)), localAffinity = true)


  def processResult = {
    case ack @ ClauseAddedAck(_, clauseId) =>
      responseWithoutStopActor {
        URI { href =>
          respondWithHeader(RawHeader("Location", href.resolve(s"match/$clauseId").toString)) {
            complete(Accepted)
          }
        }
      }
      startGathering()
  }
}
