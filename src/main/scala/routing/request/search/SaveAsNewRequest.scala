package routing.request.search

import akka.actor.ActorRef
import akka.contrib.pattern.ClusterClient.{Send, SendToAll}
import domain.search.DependencyGraph.{CycleInDirectedGraphError, PersistedAck, Edge}
import routing.request.PerRequest
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._

case class SaveAsNewRequest(ctx: RequestContext, clusterClient: ActorRef, sourceTemplateId: String, newName: String) extends PerRequest {

  import domain.search._
  import StoredQueryCommandQueryProtocol._

  var clauses = List[BoolQueryClause]()
  val newStoredQueryId = newName.hashCode().toString

  clusterClient ! Send(storedQueryViewRegion, GetAsBoolClauseQuery(sourceTemplateId), localAffinity = true)

  def processResult = {
    case BoolClauseResponse(_, _, sourceClauses, _) =>
      context.become(iterating)
      clauses = sourceClauses
      self ! "Tick"
  }

  def iterating: Receive = {

    case "Tick" =>
      clauses match {

        case Nil =>
          clusterClient ! Send(storedQueryRegion, NameCommand(newStoredQueryId, newName), localAffinity = true)

        case (clause @ NamedBoolClause(storedQueryId, occurrence, _, _)) :: xs =>
          context.become(dependencyGraphChecking(clause, xs))
          clusterClient ! SendToAll(storedQueryDependencyGraphSingleton,
            DependencyGraph.AddOccurredEdgeCommand(Edge(newStoredQueryId, storedQueryId), occurrence))

        case clause :: xs =>
          context.become(clauseAdded(xs))
          clusterClient ! Send(storedQueryRegion, AddClauseCommand(newStoredQueryId, clause), localAffinity = true)
      }

    case NamedAck(storedQueryId) =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", href.resolve(storedQueryId).toString)) {
            complete(Created)
          }
        }
      }
  }

  def clauseAdded(rest: List[BoolQueryClause]): Receive = {
    case ack: ClauseAddedAck => {
      context.become(iterating)
      clauses = rest
      self ! "Tick"
    }
  }

  def dependencyGraphChecking(clause: NamedBoolClause , rest: List[BoolQueryClause]): Receive = {

    case PersistedAck(_) =>
      context.become(clauseAdded(rest))
      clusterClient ! Send(storedQueryRegion, AddClauseCommand(newStoredQueryId, clause), localAffinity = true)

    case CycleInDirectedGraphError =>
      log.error("CycleInDirectedGraphError")
  }
}
