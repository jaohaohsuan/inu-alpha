package frontend.storedFilter

import akka.actor.Props
import frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.{DefaultFormats, Formats}
import spray.http.StatusCodes._
import spray.routing.RequestContext

case class StoredFilter(title: String) {
  require( title.nonEmpty, "title field is required")
}

object RenameRequest {
  def props(filterId: String)(implicit ctx: RequestContext) = Props(classOf[RenameRequest], ctx, filterId)
}

case class RenameRequest(ctx: RequestContext, filterId: String) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats
  import domain.storedFilter.StoredFilterAggregateRoot._

  entity(as[StoredFilter]) { entity => _ =>
    context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! Rename(filterId, entity.title)
  }(ctx)


  def processResult = {
    case UpdatedAck =>
      response {
        complete(OK)
      }
  }
}