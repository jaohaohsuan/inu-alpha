package frontend.storedFilter

import akka.actor.Props
import frontend.{CollectionJsonSupport, PerRequest}
import domain.storedFilter.StoredFilterAggregateRoot._
import spray.routing.RequestContext

case class NewFilter(title: String, tags: Option[String]) {
  require( title.nonEmpty, "title field is required")
}

object NewFilterRequest {
  def props(implicit ctx: RequestContext) = Props(classOf[NewFilterRequest], ctx)
}

case class NewFilterRequest(ctx: RequestContext) extends PerRequest with CollectionJsonSupport {

  entity(as[NewFilter]) { case NewFilter(title, tags) => _ =>
    context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! CreateNewStoredFilter(title, tags)
  } (ctx)

  import spray.http.StatusCodes._

  def processResult = {
    case ItemCreated(e) =>
      response {
        complete(Created)
      }
  }
}
