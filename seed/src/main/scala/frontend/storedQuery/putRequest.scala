package frontend.storedQuery.putRequest

import akka.actor.Props
import domain.storedQuery.StoredQueryAggregateRoot.{UpdatedAck, UpdateStoredQuery}
import frontend.PerRequest
import protocol.storedQuery.Exchange.StoredQueryItem
import spray.http.StatusCodes._
import spray.routing.RequestContext

object UpdateStoredQueryItemRequest {
  def prop(entity: StoredQueryItem)(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[UpdateStoredQueryItemRequest], ctx, storedQueryId, entity.title, entity.tags)
}

case class UpdateStoredQueryItemRequest(ctx: RequestContext, storedQueryId: String, title: String, tags: Option[String]) extends PerRequest {

  context.actorSelection("/user/aggregateRootProxy") ! UpdateStoredQuery(storedQueryId, title, tags)

  def processResult = {
    case UpdatedAck =>
      response {
        complete(OK)
      }
  }
}