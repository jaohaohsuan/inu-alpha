package com.inu.frontend.storedquery

import akka.actor.Props
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing.RequestContext
import com.inu.frontend.PerRequest
import com.inu.protocol.storedquery.messages._
import scala.language.implicitConversions

trait Clause[A] {
  def as(clause :A ): BoolClause
}

object Clause {

  implicit val namedClause = new Clause[NamedClause] {
    def as(c: NamedClause): BoolClause = c
  }

  implicit val matchClause = new Clause[MatchClause] {
    def as(c: MatchClause): BoolClause = c
  }

  implicit val spanNearClause = new Clause[SpanNearClause] {
    def as(c: SpanNearClause): BoolClause = c
  }
}


object AddClauseRequest {

  def props[A: Clause](entity: A)(implicit ctx: RequestContext, storedQueryId: String) = {
    val m = implicitly[Clause[A]]
    Props(classOf[AddClauseRequest], ctx, storedQueryId, m.as(entity))
  }
}


case class AddClauseRequest(ctx: RequestContext, storedQueryId: String, clause: BoolClause) extends PerRequest {

  context.actorSelection("/user/storedq-agg-proxy") ! AddClause(storedQueryId, clause)

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




