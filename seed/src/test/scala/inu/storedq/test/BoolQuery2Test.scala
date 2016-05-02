package inu.storedq.test

import org.scalatest.{FlatSpec, Matchers}
import protocol.storedQuery.SpanNearBoolClause
import read.storedQuery.BoolQuery2._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

/**
  * Created by henry on 5/1/16.
  */
class BoolQuery2Test extends FlatSpec with Matchers {

  "Convert SpanNearBoolClause to Span Near Query" should "like this" in {
    val clause = SpanNearBoolClause(List("keyword1","keyword2"), "agent", 10, true, "must")
    val MultiSpanNearQuery(json) = clause
    info(pretty(render(json)))
  }
}
