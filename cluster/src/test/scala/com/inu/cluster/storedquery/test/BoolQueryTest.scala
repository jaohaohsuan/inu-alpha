package com.inu.cluster.storedquery.test

import com.inu.cluster.storedquery.elasticsearch.{BoolQuery, _}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{FlatSpec, Matchers}
import com.inu.protocol.storedquery.messages._

/**
  * Created by henry on 5/1/16.
  */
class BoolQueryTest extends FlatSpec with Matchers {

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

    val namedClause = NamedClause("1", "untitled", "should", Some(Map((1, MatchClause("owl goose", "customer*", "or", "must_not")))))

    val BoolQuery(json) = List(namedClause)

    //info(pretty(render(json)))
    json \ "bool" \ "should" match {
      case  JArray(list) => list should have size 1
      case unexpected => fail(s"unexpected matching ${unexpected}")
    }

  }

  "boolQuery" should "be empty if NamedClause's cluases empty" in {
    val clause1 = NamedClause("r0", "r0", "must")
    val BoolQuery(json) = List(clause1)
    val JObject(xs) = json \ "bool"

    xs.filterNot { case ("minimum_should_match",v) => true  } should have size 0

  }

  "boolQuery" should "be empty if storedQuery's clauses empty" in {
    val BoolQuery(json2) = StoredQuery("0", "0").clauses.values
    val JObject(yx) = json2 \ "bool"

    yx.filterNot { case ("minimum_should_match",v) => true  } should have size 0
  }

}
