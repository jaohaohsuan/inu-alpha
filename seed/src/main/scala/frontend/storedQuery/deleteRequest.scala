package frontend.storedQuery.deleteRequest

import akka.actor.Props
import domain.storedQuery.StoredQueryAggregateRoot.{ClausesEmptyAck, ClausesRemovedAck, RemoveClauses, ResetOccurrence}
import frontend.PerRequest
import spray.http.StatusCodes._
import spray.routing.RequestContext

object RemoveClauseRequest {
  def props(clauseId: Int)(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[RemoveClauseRequest], ctx, storedQueryId, clauseId)
}

case class RemoveClauseRequest(ctx: RequestContext, storedQueryId: String, clauseId: Int) extends PerRequest {

  context.actorSelection(protocol.storedQuery.NameOfAggregate.root.client) ! RemoveClauses(storedQueryId, List(clauseId))

  def processResult: Receive = {
    case ClausesRemovedAck =>
      response {
        complete(NoContent)
      }

    case ClausesEmptyAck =>
      response {
        complete(NotFound)
      }
  }
}

object ResetOccurrenceRequest {
  def props(occurrence: String)(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[ResetOccurrenceRequest], ctx, storedQueryId, occurrence)
}

case class ResetOccurrenceRequest(ctx: RequestContext, storedQueryId: String, occurrence: String) extends PerRequest {

  context.actorSelection(protocol.storedQuery.NameOfAggregate.root.client) ! ResetOccurrence(storedQueryId, occurrence)

  def processResult: Receive = {
    case ClausesRemovedAck =>
      response {
        complete(NoContent)
      }
    case ClausesEmptyAck =>
      response {
        complete(NotFound)
      }

  }
}
