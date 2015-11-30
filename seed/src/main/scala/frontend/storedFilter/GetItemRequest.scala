package frontend.storedFilter

import akka.actor.Props
import akka.pattern._
import elastic.ImplicitConversions._
import es.indices.logs
import frontend.CollectionJsonSupport._
import frontend.{CollectionJsonSupport, PerRequest}
import org.elasticsearch.client.Client
import org.json4s.JsonDSL._
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats, _}
import spray.http.StatusCodes._
import spray.routing._

object GetItemRequest {
  def props(`type`: String, id: String)(implicit ctx: RequestContext, client: Client) = 
    Props(classOf[GetItemRequest],ctx, client, `type`, id)
}

case class GetItemRequest(ctx: RequestContext, private implicit val client: Client, `type`: String, id: String) extends PerRequest with CollectionJsonSupport {

  import context.dispatcher
  implicit def json4sFormats: Formats =  DefaultFormats

  private val occurs = "must" :: "must_not" :: "should" :: Nil

  def mappingProperties: Directive1[List[String]] = onSuccess(logs.getProperties(`type`))

  requestUri { uri => _ =>
    import es.indices.storedFilter._
    (for {
        resp <- prepareGet(`type`, id)
          .setFetchSource("title", null)
          .execute().future
      } yield {
        if(resp.isExists) read[Map[String, Any]](resp.getSourceAsString).asTemplate else Map("title" -> "temporary").asTemplate
      }) pipeTo self
  }(ctx)

  def processResult = {
    case source: JObject =>
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          requestUri { uri =>
            item(source) { json =>
              mappingProperties { properties =>
                val sections = occurs map { occur => ("rel" -> "section") ~~ ("name" -> occur) ~~ ("href" -> s"${uri.withPath(uri.path / occur)}") }
                val options = properties map { field => ("rel" -> "option") ~~ ("href" -> s"$uri/$field") ~~ ("name" -> field)}

                complete(OK, json.transformField {
                  case ("items", JArray(x :: Nil)) => ("items", x.transformField { case ("links", _) => ("links", sections ++ options) } :: Nil)
                })
              }
            }
          }
        }
      }
  }
}
