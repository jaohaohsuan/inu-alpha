package routing.request.search

import akka.actor.{ActorRef, Props, Terminated}
import akka.contrib.pattern.ClusterClient.{Send, SendToAll}
import routing.request._
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing._

case class ProcessingState(clausePath: String, lastVersion: Int, gatherProps: Option[Props] = None)


case class AddClauseRequest(ctx: RequestContext,
                            clusterClient: ActorRef,
                            storedQueryId: String) extends PerRequest {

  import domain.search._
  import DependencyGraph._
  import StoredQueryCommandQueryProtocol._
  import context._

  def processResult: Receive = {

    case routing.StoredQueryRoute.NamedClause(clauseStoredQueryId, name ,occur) =>
      become(addingNamedBoolClause(NamedBoolClause(clauseStoredQueryId, occur)))
      clusterClient ! SendToAll(storedQueryDependencyGraphSingleton, AddOccurredEdgeCommand(Edge(storedQueryId, clauseStoredQueryId), occur))

    case routing.StoredQueryRoute.MatchClause(query, operator, occur) =>
      become(addingMatchClause())
      clusterClient ! Send(storedQueryRegion, AddClauseCommand(storedQueryId, MatchClause(query, operator, occur)), localAffinity = true)

  }

  def addingNamedBoolClause(clause: NamedBoolClause): Receive = {

    case CycleInDirectedGraphError =>
      response {
        complete(NotAcceptable)
      }

    case PersistedAck(_) =>
      become(watchingPropagatorComplete(s"named/${clause.hashCode}"))
      watch(actorOf(Gathering.props(clusterClient,
        storedQueryId,
        None,
        Some(List(ChainLink(storedQueryId, clause.storedQueryId, clause.occurrence))))))
  }

  def addingMatchClause(): Receive = {
    case ack @ ClauseAddedAck(_, _, clauseId) =>
      become(watchingPropagatorComplete(s"match/$clauseId"))
      watch(actorOf(Gathering.props(clusterClient, storedQueryId, Some(ack))))
  }


  def watchingPropagatorComplete(clausePath: String): Receive = {

    case Terminated(gathering) =>
      log.info(s"$gathering is terminated")
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", href.resolve(clausePath).toString)) {
            complete(Created)
          }
        }
      }
  }
}