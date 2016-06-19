package com.inu.frontend.storedquery

import akka.actor.Props
import com.inu.frontend.{CollectionJsonSupport, Pagination, PerRequest}
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse}
import spray.routing._
import spray.http.StatusCodes._
import org.json4s._
import akka.pattern._
import org.json4s.JsonAST.{JArray, JField, JString}
import shapeless._

import scala.concurrent.Future

/**
  * Created by henry on 6/19/16.
  */
object PreviewRequest {
  def props(s: SearchRequestBuilder)(implicit ctx: RequestContext) = {
    Props(classOf[PreviewRequest], ctx, s)
  }
}

case class PreviewRequest(ctx: RequestContext, s: SearchRequestBuilder) extends PerRequest with CollectionJsonSupport{

  import com.inu.frontend.elasticsearch.ImplicitConversions._
  import org.json4s.native.JsonMethods._
  import context.dispatcher

  //val storedQueryQuery = compact(render(item \ "query"))
  s.execute().future pipeTo self

  def processResult: Receive = {
    case res: SearchResponse =>
      response {
        requestUri { uri =>
          pagination(res)(uri) { p =>
            val links = JField("links", JArray(p.links))
            val href = JField("href", JString(s"$uri"))
            complete(OK, href :: links :: Nil)
          }
        }
      }
  }
}
