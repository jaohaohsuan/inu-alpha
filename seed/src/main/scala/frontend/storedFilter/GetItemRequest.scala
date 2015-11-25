package frontend.storedFilter

import akka.actor.Props
import frontend.CollectionJsonSupport._
import frontend.{CollectionJsonSupport, PerRequest}
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.Client
import org.json4s.{DefaultFormats, Formats}
import spray.routing.RequestContext
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import akka.pattern._
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

import scala.concurrent.Future

object GetItemRequest {
  def props(typ: String, id: String)(implicit ctx: RequestContext, client: Client) = Props(classOf[GetItemRequest],ctx, client, typ, id)
}

case class GetItemRequest(ctx: RequestContext, private implicit val client: Client, typ: String, id: String) extends PerRequest with CollectionJsonSupport {

  import context.dispatcher
  implicit def json4sFormats: Formats =  DefaultFormats

  requestUri { uri => _ =>
    import es.indices.storedFilter._
      val getItem: Future[GetResponse] = prepareGet(typ, id)
        .execute()
        .asFuture
     getItem pipeTo self
  }(ctx)

  def processResult = {
    case r: GetResponse =>
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          item(read[Map[String, Any]](r.getSourceAsString)) { json =>
            complete(OK, json)
          }
        }
      }
  }
}
