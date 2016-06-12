package com.inu.cluster.storedquery.test

import com.inu.protocol.storedquery.messages.MatchClause
import com.inu.cluster.storedquery.elasticsearch._
import com.inu.protocol.storedquery.messages._
import org.json4s._
import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by henry on 5/12/16.
  */
class StoredQueryMarshallingTest  extends FlatSpec with Matchers {

  "keywords" should "like this" in {

    val c0 = 0 -> MatchClause("a b c", "dialogs", "AND", "must")
    val c1 = 1 -> MatchClause("a b c", "dialogs", "AND", "must")
    val c2 = 2 -> MatchClause("d d", "dialogs", "AND", "must")
    val c3 = 3 -> SpanNearClause("y z", "dialogs", 1, true, "should")

    val c4 = 4 -> SpanNearClause("g h", "dialogs", 1, true, "should")
    val c5 = 5 -> NamedClause("456", "query1", "must", Map(c4))

    val sq0 = StoredQuery("123", "query0", Map(c0, c1, c2, c3, c5))

    val QueryTerms(_,terms) = sq0
    info(s"$terms")
    terms should contain allOf ("a","b", "c", "d", "y", "z", "g", "h")
  }

  "occurs" should "like this" in {

    val c0 = 0 -> MatchClause("a b c", "dialogs", "AND", "must")
    val c1 = 1 -> MatchClause("a b c", "dialogs", "AND", "should")
    val c2 = 2 -> MatchClause("d d", "dialogs", "AND", "must_not")
    val c3 = 3 -> SpanNearClause("y z", "dialogs", 1, true, "should")

    val c4 = 4 -> SpanNearClause("g h", "dialogs", 1, true, "should")
    val c5 = 5 -> NamedClause("456", "query1", "must", Map(c4))

    val Occurs(json) = Map(c0, c1, c2, c3, c5)

    def countOf(occur: String, count: Int) = {
      val JArray(ls) = json \ occur
      ls should have size count
    }

    countOf("must", 2)
    countOf("should", 2)
    countOf("must_not", 1)
    //info(s"${pretty(render(json))}")
  }

}
