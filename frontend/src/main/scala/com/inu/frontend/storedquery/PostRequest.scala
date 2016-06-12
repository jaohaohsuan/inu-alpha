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










