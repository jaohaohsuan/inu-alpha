package com.inu.frontend.storedquery

import akka.actor.Props
import com.inu.frontend.PerRequest
import com.inu.protocol.storedquery.messages.{Command, RejectAck, StoredQueryCreatedAck}
import spray.http.StatusCodes._
import spray.routing.RequestContext

/**
  * Created by henry on 6/15/16.
  */
object AdminRequest {
  def props(message: Command)(implicit ctx: RequestContext) =
    Props(classOf[AdminRequest], ctx, message)
}

case class AdminRequest(ctx: RequestContext, message: Command) extends PerRequest {

  println(s"Admin Request $message")

  context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! message

  def processResult: Receive = {
    case StoredQueryCreatedAck("temporary") =>
      response {
        complete(Created)
      }
    case RejectAck(msg) =>
      response {
        complete(InternalServerError, msg)
      }
  }
}
