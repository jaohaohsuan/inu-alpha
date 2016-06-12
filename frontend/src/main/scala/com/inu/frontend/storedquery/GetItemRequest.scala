package com.inu.frontend.storedquery

import akka.actor.Props
import com.inu.frontend.CollectionJsonSupport._
import spray.routing.RequestContext
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.JsonAST.{JArray, JField, JString}

object GetItemRequest {
  def props(implicit ctx: RequestContext, client: org.elasticsearch.client.Client, storedQueryId: String) =
    Props(classOf[GetItemRequest], ctx, client: org.elasticsearch.client.Client,  storedQueryId)
}

case class GetItemRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client, storedQueryId: String)
  extends PerRequest with CollectionJsonSupport {

  import com.inu.frontend.elasticsearch.ImplicitConversions._
  import akka.pattern._
  import org.elasticsearch.action.get.GetResponse
  import context.dispatcher
  import spray.http.StatusCodes._
  import org.json4s.native.JsonMethods._
  import org.json4s.JsonDSL._

  client.prepareGet("stored-query", ".percolator", storedQueryId).setFetchSource(Array("item"), null).execute().future pipeTo self

  def processResult: Receive = {
    case res: GetResponse =>
      response {
        requestUri(implicit uri => {
          respondWithMediaType(`application/vnd.collection+json`) {
            val source = parse(s"${res.getSourceAsString}")
            val items = JField("items", JArray((source \ "item" transformField {
              case JField("href", _) => ("href", JString(s"$uri"))
            }) :: Nil))

            val href = JField("href", JString("""/\d+$""".r.replaceFirstIn(s"$uri", "")))
            val template = JField("template", "data" -> (source \ "item" \ "data"))
            complete(OK, href :: items :: template :: Nil)
          }
        })
      }
  }
}