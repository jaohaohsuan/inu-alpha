package routing.request.search.template

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.SendToAll
import domain.search.template.Graph
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._
import domain.search.template.CommandQueryProtocol._

  case class AddNamedClauseRequest(ctx: RequestContext,
                                   clusterClient: ActorRef,
                                   templateId: String,
                                   clauseTemplateId: String,
                                   occur: String) extends PerRequest with AutoGathering {

    clusterClient ! SendToAll(graphSingleton, Graph.AddEdgeCommand(templateId, clauseTemplateId, occur))

    def processResult: Receive = {
      case Graph.DirectedCyclesNotAccepted =>
        response {
          complete(NotAcceptable)
        }
      case Graph.Inconsistency(error) =>
        response {
          complete(InternalServerError, error)
        }
      case Graph.PersistedAck(event) =>
        responseWithoutStopActor {
          URI { href =>
            respondWithHeader(RawHeader("Location", href.resolve(s"named/${clauseTemplateId.hashCode}").toString)) {
              complete(Accepted)
            }
          }
        }
        context.become(gathering(initialSegments = (templateId, (clauseTemplateId, occur)) :: Nil))
        clusterClient ! SendToAll(graphSingleton, Graph.Get(templateId))
    }
  }


