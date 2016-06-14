package com.inu.frontend.storedquery

import akka.actor.Props
import spray.routing._
import com.inu.protocol.storedquery.messages._
import com.inu.frontend.PerRequest
import spray.httpx.unmarshalling._


object AddClauseRequest {
  def props[A <: BoolClause](entity: A)(implicit ctx: RequestContext, storedQueryId: String) = {
    Props(classOf[AddClauseRequest], ctx, storedQueryId, entity)
  }
}

case class AddClauseRequest(ctx: RequestContext, storedQueryId: String, clause: BoolClause) extends PerRequest {

  import spray.http.HttpHeaders.RawHeader
  import spray.http.StatusCodes._

  context.actorSelection("/user/StoredQueryRepoAggRoot-Proxy") ! AddClause(storedQueryId, clause)

  def processResult = {

    case ClauseAddedAck(clauseId) =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$clauseId")){
            complete(Created)
          }
        }
      }
//    case CycleInDirectedGraphError =>
//      response {
//        complete(NotAcceptable)
//      }
  }
}