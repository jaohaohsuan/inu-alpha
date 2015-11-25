package frontend.storedFilter

import akka.actor.Props
import frontend.{CollectionJsonSupport, PerRequest}
import domain.storedFilter.StoredFilterAggregateRoot._
import org.json4s.{DefaultFormats, Formats}
import spray.http.HttpHeaders.RawHeader
import spray.routing.RequestContext

case class NewFilter(title: String) {
  require( title.nonEmpty, "title field is required")
}

object NewFilterRequest {
  def props(implicit ctx: RequestContext) = Props(classOf[NewFilterRequest], ctx)
}

case class NewFilterRequest(ctx: RequestContext) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats

  path(Segment) { typ =>
    entity(as[NewFilter]) { case NewFilter(title) => _ =>
      context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! CreateNewStoredFilter(typ, title)
    }
  } (ctx)

  import spray.http.StatusCodes._

  def processResult = {
    case ItemCreated(id, _, _) =>
      response {
        requestUri { uri =>
          respondWithHeader(RawHeader("Location", s"$uri/$id".replaceAll("""/(\d|temporary)+(?=/\d)""", ""))){
            complete(Created)
          }
        }
      }
  }
}
