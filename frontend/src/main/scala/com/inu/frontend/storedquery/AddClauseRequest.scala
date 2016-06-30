package com.inu.frontend.storedquery

import akka.actor.Props
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.inu.frontend.CollectionJsonSupport._
import spray.routing._
import com.inu.protocol.storedquery.messages._
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import org.json4s.JsonAST.{JField, JString}
import org.json4s.JsonDSL._


object AddClauseRequest {
  def props[A <: BoolClause](entity: A)(implicit ctx: RequestContext, storedQueryId: String) = {
    Props(classOf[AddClauseRequest], ctx, storedQueryId, entity)
  }
}

case class AddClauseRequest(ctx: RequestContext, storedQueryId: String, clause: BoolClause) extends PerRequest with CollectionJsonSupport {

  import spray.http.HttpHeaders.RawHeader
  import spray.http.StatusCodes._

  context.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/StoredQueryRepoAggRoot",
    settings = ClusterSingletonProxySettings(context.system)
  )) ! AddClause(storedQueryId, clause)

  //context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! AddClause(storedQueryId, clause)

  def processResult = {
    case ClauseAddedAck(clauseId) =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$clauseId")){
            complete(Created)
          }
        }
      }
    case RejectAck(err) =>
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          requestUri { uri =>
            val href = JField("href", JString(s"$uri"))
            val error = JField("error", ("title" -> "AddClauseRequest") ~~ ("code" -> "1") ~~ ("message" -> err))
            complete(NotAcceptable, href :: error :: Nil)
          }
        }
      }
  }
}