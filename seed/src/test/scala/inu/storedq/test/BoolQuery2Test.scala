package inu.storedq.test

import org.scalatest.{FlatSpec, Matchers}
import protocol.storedQuery._
import read.storedQuery.BoolQuery._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import read.storedQuery.{BoolQuery, MultiSpanNearQuery}

/**
  * Created by henry on 5/1/16.
  */
class BoolQuery2Test extends FlatSpec with Matchers {

  "Convert SpanNearBoolClause to Span Near Query" should "like this" in {
    val clause = SpanNearClause("keyword1 keyword2", "agent*", 10, true, "must")
    val MultiSpanNearQuery(json) = clause

    (json \ "bool" \ "must")(0) \ "bool" \ "should" match {
      case JArray(list) => list should have size 3 // agent0, agent1, agent2
      case _ => fail("unexpected matching")
    }

    info(pretty(render(json)))
  }

  "Convert clause to bool query" should "like" in {
    val clause = MatchClause("owl goose", "customer*", "or", "must_not")
    val BoolQuery(json) = List(clause)
    (json \ "bool" \ "must_not")(0) \ "multi_match" \ "query"  match {
      case JString(q) => assert(q == clause.query)
      case _ => fail("unexpected matching")
    }
    info(pretty(render(json)))
  }

  "boolQuery" should "" in {

    val namedClause = NamedClause("1", "untitled", "should", Map((1, MatchClause("owl goose", "customer*", "or", "must_not"))))

    val BoolQuery(json) = List(namedClause)

    json \ "bool" \ "should" match {
      case  JArray(list) => list should have size 1
      case _ => fail("unexpected matching")
    }
    info(pretty(render(json)))
  }
}
