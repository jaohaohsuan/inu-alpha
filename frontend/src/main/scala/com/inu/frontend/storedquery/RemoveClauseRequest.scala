package com.inu.frontend.storedquery

import akka.actor.Props
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.inu.frontend.PerRequest
import com.inu.protocol.storedquery.messages.Command
import spray.routing.RequestContext
import com.inu.protocol.storedquery.messages._
import spray.http.StatusCodes._

/**
  * Created by henry on 6/14/16.
  */
object RemoveClauseRequest {
  def props(message: Command)(implicit ctx: RequestContext) =
    Props(classOf[RemoveClauseRequest], ctx, message)
}

case class RemoveClauseRequest(ctx: RequestContext, message: Command) extends PerRequest {

  context.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/StoredQueryRepoAggRoot",
    settings = ClusterSingletonProxySettings(context.system)
  )) ! message
  //context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! message

  def processResult: Receive = {
    case ClausesRemovedAck =>
      response {
        complete(NoContent)
      }
  }
}