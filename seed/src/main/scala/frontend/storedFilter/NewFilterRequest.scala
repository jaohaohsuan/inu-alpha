package frontend.storedFilter

import akka.actor.Props
import domain.storedFilter.StoredFilterAggregateRoot._
import frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.{DefaultFormats, Formats}
import spray.http.HttpHeaders.RawHeader
import spray.routing.RequestContext

case class NewFilter(title: String) {
  require( title.nonEmpty, "title field is required")
}

object NewFilterRequest {
  def props(typ: String, referredId: Option[String] = None)(implicit ctx: RequestContext) = Props(classOf[NewFilterRequest], ctx, typ, referredId)
}

case class NewFilterRequest(ctx: RequestContext, typ: String, referredId: Option[String]) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats

  entity(as[NewFilter]) { case NewFilter(title) => _ =>
    context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! CreateNewStoredFilter(typ, title, referredId)
  } (ctx)

  import spray.http.StatusCodes._

  def processResult = {
    case NoContentAck =>
      response {
        complete(NoContent)
      }
    case ItemCreatedAck(id) =>
      response {
        requestUri { uri =>
          respondWithHeader(RawHeader("Location", s"$uri/$id".replaceAll("""/(\d|temporary)+(?=/\d)""", ""))){
            complete(Created)
          }
        }
      }
  }
}
