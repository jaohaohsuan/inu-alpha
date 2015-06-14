package routing.request.search.template

import akka.actor.{Terminated, ActorRef}
import akka.contrib.pattern.ClusterClient.{ Send, SendToAll }
import domain.search.template.Graph.{Routes, Inconsistency, Get}
import domain.search.template.{Gathering, NamedBoolClause, Graph}
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._
import domain.search.template.CommandQueryProtocol._
import scala.concurrent.duration._

  case class AddNamedClauseRequest(ctx: RequestContext,
                                   clusterClient: ActorRef,
                                   templateId: String,
                                   clauseTemplateId: String,
                                   occur: String) extends PerRequest {

    clusterClient ! SendToAll(graphSingleton, Graph.AddEdgeCommand(templateId, clauseTemplateId, occur))

    def processResult: Receive = {
      case Graph.DirectedCyclesNotAccepted =>
        response {
          complete(NotAcceptable)
        }
      case Graph.PersistedAck(event) =>
        clusterClient ! Send(templateViewRegion, GetAsBoolClauseQuery(clauseTemplateId), localAffinity = true)

      case BoolClauseResponse(_, name, clauses, version) =>
        clusterClient ! Send(templateRegion, AddClauseCommand(templateId, NamedBoolClause(clauseTemplateId, name, clauses, occur)), localAffinity = true)

      case ClauseAddedAck(_, version, clauseId) =>
        start(ProcessingState(s"named/$clauseId", version))
    }

    def start(state: ProcessingState): Unit = {
      log.info(s"Start gathering from $templateId")
      context.become(collectingRoutes(state))
      clusterClient ! SendToAll(graphSingleton, Get(templateId))
    }

    def versionChecking(state: ProcessingState): Receive = {
      case VersionResponse(_, version) =>
        log.info(s"lastAckVersion: ${state.lastVersion}, viewVersion: $version")
        if (version < state.lastVersion) {
          import context.dispatcher
          context.system.scheduler.scheduleOnce(1.seconds, clusterClient, Send(templateViewRegion, GetVersion(templateId), localAffinity = true))
        }
        else {
          context.become(gathering(state))
          context.watch(context.actorOf(state.gatherProps.get, name = "gathering"))
        }
    }

    def collectingRoutes(state: ProcessingState): Receive = {

      case Inconsistency(error) =>
        response {
          complete(InternalServerError, error)
        }
      case Routes(segments) =>
        context.become(versionChecking(state.copy(gatherProps = Some(Gathering.props(clusterClient, segments)))))
        clusterClient ! Send(templateViewRegion, GetVersion(templateId), localAffinity = true)
    }

    def gathering(state: ProcessingState): Receive = {

      case Terminated(child) =>
        log.info(s"$child is terminated")
        response {
          URI { href =>
            respondWithHeader(RawHeader("Location", href.resolve(state.clausePath).toString)) {
              complete(Accepted)
            }
          }
        }
    }
  }