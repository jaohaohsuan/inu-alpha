package frontend.storedFilter

import akka.actor.Props
import frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.{DefaultFormats, Formats}
import protocol.elastics.boolQuery.OccurrenceRegex
import spray.http.StatusCodes._
import spray.routing.RequestContext


object DeleteClauseRequest {
  def props(typ: String, filterId: String)(implicit ctx: RequestContext) = Props(classOf[DeleteClauseRequest], ctx, typ, filterId)
}

case class DeleteClauseRequest(ctx: RequestContext, typ: String, filterId: String) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats
  import domain.storedFilter.StoredFilterAggregateRoot._

  def process =
    path(OccurrenceRegex) { occur => implicit ctx =>
      context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! EmptyClauses(filterId, typ, occur)
    } ~
    path(Segment / Segment / Segment) { (field, query, clauseId) => implicit ctx =>
      context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! RemoveClause(filterId, typ, clauseId)
    }

  process(ctx)

  def processResult = {
    case ClausesRemovedAck =>
      response {
        complete(NoContent)
      }
    case ClausesEmptyAck =>
      response {
        complete(NoContent)
      }
  }

}
