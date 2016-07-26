package com.inu.frontend.storedquery

import akka.actor.Props
import com.inu.frontend.PerRequest
import com.inu.protocol.storedquery.messages._
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing.RequestContext

case class NewTemplate(title: String, tags: Option[String]){
  require( title.nonEmpty )
}

object NewTemplateRequest {
  def props(implicit ctx: RequestContext, e: NewTemplate, referredId: Option[String] = None) =
    Props(classOf[NewTemplateRequest], ctx, e, referredId)
}

case class NewTemplateRequest(ctx: RequestContext, e: NewTemplate, referredId: Option[String] = None) extends PerRequest {

  context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! CreateNewStoredQuery(e.title, referredId, e.tags)

  def processResult = {
    case StoredQueryCreatedAck(id)  =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$id".replaceAll("""/(\d|temporary)+(?=/\d)""", ""))){
            complete(Created)
          }
        }
      }
  }
}