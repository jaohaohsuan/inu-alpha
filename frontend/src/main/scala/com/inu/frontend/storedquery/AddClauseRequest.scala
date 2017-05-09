package com.inu.frontend.storedquery

import akka.actor.Props
import com.inu.frontend.CollectionJsonSupport._
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import com.inu.protocol.storedquery.messages._
import org.json4s.JsonAST.{JField, JString}
import org.json4s.JsonDSL._
import spray.routing._


object AddClauseRequest {
  def props[A <: BoolClause](entity: A)(implicit ctx: RequestContext, storedQueryId: String) = {
    Props(classOf[AddClauseRequest], ctx, storedQueryId, entity)
  }
}

case class AddClauseRequest(ctx: RequestContext, storedQueryId: String, clause: BoolClause) extends PerRequest with CollectionJsonSupport {

  import spray.http.HttpHeaders.RawHeader
  import spray.http.StatusCodes._

  context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! AddClause(storedQueryId, clause)

  def processResult = {
    case ClauseAddedAck(clauseId) =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"${href.resolve(s"${href.getPath}/$clauseId")}")){
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