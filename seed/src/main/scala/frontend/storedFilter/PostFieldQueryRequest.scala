package frontend.storedFilter

import akka.actor.Props
import frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.JsonAST.{JArray, JValue}
import org.json4s.{DefaultFormats, Formats}
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.httpx.unmarshalling.FromRequestUnmarshaller
import spray.routing.{RequestContext, Route}


object PostFieldQueryRequest {
  def props(typ: String, filterId: String, field: String)(implicit ctx: RequestContext) = Props(classOf[PostFieldQueryRequest], ctx, typ, filterId, field)
}

case class TermQuery(occurrence: String, value: JValue)
case class TermsQuery(occurrence: String, value: JArray)
case class RangeQuery(occurrence: String, gte: JValue, lte: JValue)

case class PostFieldQueryRequest(ctx: RequestContext, typ: String, filterId: String, field: String) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats
  import domain.storedFilter.StoredFilterAggregateRoot._

  def clausePath[T](name: String)(implicit um: FromRequestUnmarshaller[T]): Route =
    path(name) {
      entity(as[T]) { e => implicit ctx: RequestContext =>
         val boolClause = e match {
          case TermQuery(occur, value) =>
            protocol.storedFilter.TermQuery(occur, field, value)
          case TermsQuery(occur, value) =>
            protocol.storedFilter.TermsQuery(occur, field, value)
          case RangeQuery(occur, gte, lte) =>
            protocol.storedFilter.RangeQuery(occur, field, gte, lte)
        }
        context.actorSelection(protocol.storedFilter.NameOfAggregate.root.client) ! AddClause(filterId, typ, boolClause)
      }
    }

  val process = clausePath[TermQuery]("term") ~ clausePath[TermsQuery]("terms") ~ clausePath[RangeQuery]("range")

  process(ctx)

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