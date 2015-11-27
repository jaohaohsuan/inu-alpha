package frontend.storedFilter

import akka.actor.Props
import frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.JsonAST.{JValue, JObject}
import org.json4s.{DefaultFormats, Formats}
import protocol.storedFilter.BoolClause
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.httpx.unmarshalling.FromRequestUnmarshaller
import spray.routing.{Directive1, Route, RequestContext}


object PostFieldQueryRequest {
  def props(typ: String, filterId: String, field: String)(implicit ctx: RequestContext) = Props(classOf[PostFieldQueryRequest], ctx, typ, filterId, field)
}

case class TermQuery(occurrence: String, value: JValue)

case class PostFieldQueryRequest(ctx: RequestContext, typ: String, filterId: String, field: String) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats
  import domain.storedFilter.StoredFilterAggregateRoot._

  path("term") {
    entity(as[TermQuery]) { input => _ =>
      val clause = protocol.storedFilter.TermQuery(filterId, input.occurrence, typ, field, input.value)
      context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! AddClause(clause)
    }
  } (ctx)

  def processResult = {
    case ClauseAddedAck(id) =>
      response {
        requestUri { uri =>
          respondWithHeader(RawHeader("Location", s"$uri/$id")){
            complete(Created)
          }
        }
      }
  }
}