package frontend.storedFilter

import akka.actor.Props
import frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.{DefaultFormats, Formats}
import spray.routing.RequestContext
import spray.http.StatusCodes._
import org.json4s.JsonDSL._
import org.elasticsearch.client.Client

object QueryRequest {
  def props(typ: String)(implicit ctx: RequestContext, client: Client) =
    Props(classOf[QueryRequest], typ, ctx, client)
}

case class QueryRequest( typ: String, ctx: RequestContext, client: Client) extends PerRequest with CollectionJsonSupport {

  implicit def json4sFormats: Formats =  DefaultFormats

  self ! "?"

  def processResult = {
    case _ =>
      response {
        requestUri { uri =>
          collection { json =>
            complete(OK, json.mapField {
              case ("template", _) => "template" -> NewFilter("untitled").asTemplate
              case ("links", _) => "links" -> List(("rel" -> "edit") ~~ ("href" -> s"${uri.withPath(uri.path / "temporary")}"))
              case x => x
            })
          }
        }
      }
  }

}

