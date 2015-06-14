package routing.request.search.template

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.Send
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing.RequestContext
import domain.search.template.CommandQueryProtocol._

case class RemoveClauseRequest(ctx: RequestContext, clusterClient: ActorRef,
                                templateId: String,
                                clauseType: String,
                                clauseId: Int) extends PerRequest with AutoGathering {

  clusterClient ! Send(templateRegion, RemoveClauseCommand(templateId, clauseId), localAffinity = true)

  def processResult: Receive = {

    case ClauseRemovedAck(_, clauseId, clause) =>
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
