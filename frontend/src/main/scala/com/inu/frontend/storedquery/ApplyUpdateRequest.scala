package com.inu.frontend.storedquery

import akka.actor.Props
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.inu.frontend.PerRequest
import com.inu.protocol.storedquery.messages._
import spray.http.StatusCodes._
import spray.routing.RequestContext

case class StoredQueryItem(title: String, tags: Option[String]) {
  require( title.nonEmpty )
}

object ApplyUpdateRequest {
  def prop(entity: StoredQueryItem)(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[ApplyUpdateRequest], ctx, UpdateStoredQuery(storedQueryId, entity.title, entity.tags))
}

case class ApplyUpdateRequest(ctx: RequestContext, update: Command)  extends PerRequest {

  context.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/StoredQueryRepoAggRoot",
    settings = ClusterSingletonProxySettings(context.system)
  )) ! update

  //context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! update

  def processResult = {
    case UpdatedAck =>
      response {
        complete(OK)
      }
  }

}
