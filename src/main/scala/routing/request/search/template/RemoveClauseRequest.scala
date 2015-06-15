package routing.request.search.template

import akka.actor.{Terminated, ActorRef}
import akka.contrib.pattern.ClusterClient.{SendToAll, Send}
import akka.util.Timeout
import domain.search.template.{Graph, NamedBoolClause, Gathering}
import domain.search.template.Graph._
import routing.request.PerRequest
import akka.pattern._
import spray.http.StatusCodes._
import spray.routing.RequestContext
import domain.search.template.CommandQueryProtocol._
import scala.concurrent.duration._

case class RemoveClauseRequest(ctx: RequestContext, clusterClient: ActorRef,
                                templateId: String,
                                clauseType: String,
                                clauseId: Int) extends PerRequest {

  clusterClient ! Send(templateRegion, RemoveClauseCommand(templateId, clauseId), localAffinity = true)

  def processResult: Receive = {

    case ClauseRemovedAck(_, version, clauseId, clause: NamedBoolClause) =>
      import context.dispatcher
      implicit val timeout = Timeout(2.seconds)
      (clusterClient ? SendToAll(graphSingleton, Graph.RemoveEdgeCommand(Edge(templateId, clause.templateId), clause.occur))).map {
        case PersistedAck(_) =>
          start(ProcessingState(s"$clauseType/$clauseId", version))
      }.recover {
        case _ => response { complete(NotFound) }
      }

    case ClauseRemovedAck(_, version ,clauseId, clause) =>
      start(ProcessingState(s"$clauseType/$clauseId", version))

    case ClauseNotFoundAck =>
      response { complete(NotFound) }
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
        complete(NoContent)
      }
  }
}
