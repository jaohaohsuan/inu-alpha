package routing.request.search.template

import akka.actor.{ActorRef, Props, Terminated}
import akka.contrib.pattern.ClusterClient.{Send, SendToAll}
import domain.search.template.CommandQueryProtocol._
import domain.search.template.Graph.{Edge, Get, Inconsistency, Routes}
import domain.search.template.{Gathering, Graph, MatchClause, NamedBoolClause}
import routing.SearchTemplateRoute
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._

import scala.concurrent.duration._

case class ProcessingState(clausePath: String, lastVersion: Int, gatherProps: Option[Props] = None)

  case class AddClauseRequest(ctx: RequestContext,
                                   clusterClient: ActorRef,
                                   templateId: String) extends PerRequest {

    def processResult: Receive = {

      case SearchTemplateRoute.NamedClause(clauseTemplateId, occur) =>
        context.become(addingNamedBoolClause(NamedBoolClause(clauseTemplateId, None, occur)))
        clusterClient ! SendToAll(graphSingleton, Graph.AddEdgeCommand(Edge(templateId, clauseTemplateId), occur))

      case SearchTemplateRoute.MatchClause(query, operator, occur) =>
        context.become(addingMatchClause())
        clusterClient ! Send(templateRegion, AddClauseCommand(templateId, MatchClause(query, operator, occur)), localAffinity = true)

    }
    
    def addingNamedBoolClause(clause: NamedBoolClause): Receive = {

      case Graph.DirectedCyclesNotAccepted =>
        response {
          complete(NotAcceptable)
        }
      case Graph.PersistedAck(_) =>
        clusterClient ! Send(templateViewRegion, GetAsBoolClauseQuery(clause.templateId), localAffinity = true)

      case BoolClauseResponse(clauseTemplateId , name, clauses, version) =>
        clusterClient ! Send(templateRegion, AddClauseCommand(templateId, clause.copy(clauses = clauses)), localAffinity = true)

      case ClauseAddedAck(_, version, clauseId) =>
        start(ProcessingState(s"named/$clauseId", version))
    }

    def addingMatchClause() : Receive = {
      case ClauseAddedAck(_, version, clauseId) =>
        start(ProcessingState(s"match/$clauseId", version))
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