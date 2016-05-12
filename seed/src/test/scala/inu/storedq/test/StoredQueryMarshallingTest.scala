package inu.storedq.test

import org.scalatest.{FlatSpec, Matchers}
import protocol.storedQuery.{MatchBoolClause, NamedBoolClause, SpanNearBoolClause, StoredQuery}
import read.storedQuery.{ QueryTerms, Occurs }
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

/**
  * Created by henry on 5/12/16.
  */
class StoredQueryMarshallingTest  extends FlatSpec with Matchers {

  "keywords" should "like this" in {

    val c0 = 0 -> MatchBoolClause("a b c", "agent0", "AND", "must")
    val c1 = 1 -> MatchBoolClause("a b c", "agent0", "AND", "must")
    val c2 = 2 -> MatchBoolClause("d d", "agent0", "AND", "must")
    val c3 = 3 -> SpanNearBoolClause(List("y", "z"), "agent0", 1, true, "should")

    val c4 = 4 -> SpanNearBoolClause(List("g", "h"), "agent0", 1, true, "should")
    val c5 = 5 -> NamedBoolClause("456", "query1", "must", Map(c4))

    val sq0 = StoredQuery("123", "query0", Map(c0, c1, c2, c3, c5))

    val QueryTerms(_,terms) = sq0
    info(s"$terms")
    terms should contain allOf ("a","b", "c", "d", "y", "z", "g", "h")
  }

  "occurs" should "like this" in {

    val c0 = 0 -> MatchBoolClause("a b c", "agent0", "AND", "must")
    val c1 = 1 -> MatchBoolClause("a b c", "agent0", "AND", "should")
    val c2 = 2 -> MatchBoolClause("d d", "agent0", "AND", "must_not")
    val c3 = 3 -> SpanNearBoolClause(List("y", "z"), "agent0", 1, true, "should")

    val c4 = 4 -> SpanNearBoolClause(List("g", "h"), "agent0", 1, true, "should")
    val c5 = 5 -> NamedBoolClause("456", "query1", "must", Map(c4))

    val Occurs(json) = Map(c0, c1, c2, c3, c5)

    def countOf(occur: String, count: Int) = {
      val JArray(ls) = json \ occur
      ls should have size count
    }

    countOf("must", 2)
    countOf("should", 2)
    countOf("must_not", 1)
    info(s"${pretty(render(json))}")
  }

}
