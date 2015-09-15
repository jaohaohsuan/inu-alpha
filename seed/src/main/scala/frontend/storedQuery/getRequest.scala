package frontend.storedQuery

import akka.actor.Props
import frontend.PerRequest
import org.elasticsearch.action.get.GetResponse
import spray.routing.RequestContext
import akka.pattern._
import spray.http.StatusCodes._
import read.storedQuery.StoredQueryIndex
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.JsonDSL._
import frontend.CollectionJsonSupport.`application/vnd.collection+json`

object GetStoredQueryRequest {
  def props(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[GetStoredQueryRequest], ctx, storedQueryId)
}

case class GetStoredQueryRequest(ctx: RequestContext, storedQueryId: String) extends PerRequest {

  import context.dispatcher
  import StoredQueryIndex._

  val getItem =
    prepareGet(storedQueryId).setFetchSource(null,Array("should", "must", "must_not")).setTransformSource(true)
    .request()

  StoredQueryIndex.get(getItem).map {
    case r: GetResponse => r.getSourceAsString
  }.recover { case _ => """{ "error": { } }""" } pipeTo self

  def processResult: Receive = {
    case json: String =>
      //compact(render("collection" -> (parse(json) merge JObject(("version" -> JString("1.0"))))))
      val content = "collection" -> parse(json)
      log.debug(pretty(render(content)))
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          complete(OK, compact(render(content)))
        }
      }
  }
}
