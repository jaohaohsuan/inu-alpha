package frontend.storedFilter

import akka.actor.Props
import domain.Command
import frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.{DefaultFormats, Formats}
import spray.http.StatusCodes._
import spray.routing.RequestContext

object DeleteRequest {
  def props(command: Command)(implicit ctx: RequestContext) = Props(classOf[DeleteRequest], ctx, command)
}

case class DeleteRequest(ctx: RequestContext, command: Command) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats
  import domain.storedFilter.StoredFilterAggregateRoot._

  context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! command

  def processResult = {
    case ItemDeletedAck =>
      response {
        complete(NoContent)
      }
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
