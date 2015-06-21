package routing.request.search

import akka.actor.{ActorRef, Terminated}
import akka.contrib.pattern.ClusterClient.{Send, SendToAll}
import akka.pattern._
import akka.util.Timeout
import domain.search.StoredQueryCommandQueryProtocol._
import domain.search.{DependencyGraph, Gathering, NamedBoolClause}
import routing.request.PerRequest
import spray.http.StatusCodes._
import spray.routing.RequestContext

import scala.concurrent.duration._

case class RemoveClauseRequest(ctx: RequestContext, clusterClient: ActorRef,
                                storedQueryId: String,
                                clauseType: String,
                                clauseId: Int) extends PerRequest {
  
  import DependencyGraph._
  import context._

  clusterClient ! Send(storedQueryRegion, RemoveClauseCommand(storedQueryId, clauseId), localAffinity = true)

  def processResult: Receive = {

    case ack @ ClauseRemovedAck(_, _, _, clause: NamedBoolClause) =>
      import context.dispatcher
      implicit val timeout = Timeout(2.seconds)
      (clusterClient ? SendToAll(storedQueryDependencyGraphSingleton, RemoveOccurredEdgeCommand(Edge(storedQueryId, clause.storedQueryId), clause.occurrence))).map {
        case PersistedAck(_) =>
          watch(actorOf(Gathering.props(clusterClient, storedQueryId, Some(ack))))
      }.recover {
        case _ => response { complete(NotFound) }
      }

    case ack : ClauseRemovedAck =>
      watch(actorOf(Gathering.props(clusterClient, storedQueryId, Some(ack))))

    case ClauseNotFoundAck =>
      response { complete(NotFound) }

    case Terminated(child) =>
      log.info(s"$child is terminated")
      response {
        complete(NoContent)
      }
  }
}
