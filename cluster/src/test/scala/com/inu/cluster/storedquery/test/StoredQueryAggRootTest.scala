package com.inu.cluster.storedquery.test

import com.inu.cluster.storedquery.StoredQueryRepoAggRoot._
import com.inu.protocol.storedquery.messages._
import org.scalatest.{FlatSpec, Matchers}

class StoredQueryAggRootTest extends FlatSpec with Matchers {

  "query0" should "be added" in {

    val stage0 = StoredQueries()

    val stage2 = stage0.update(ItemCreated("123", "query0", None, Set("demo")))

    assert(stage2.items.exists(_ == ("123", StoredQuery("123", title = "query0", Map.empty, Set("demo")))))
    stage2.paths should be (empty)
    val x :: xs = stage2.changes
    x should contain ("123")
  }

  "query0 ref query1" should "be no cyclic error" in {

    val stage0 = StoredQueries()

    val stage1 = stage0.update(ItemCreated("0", "query0", None, Set("demo")))
    val stage2 = stage1.update(ItemCreated("1", "query1", None, Set("test")))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedClause("1", "query1", "must"))))
    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))
    val x :: xs = stage3.changes
    x should contain ("0")
    x should have size 1
    stage3.changes.flatten should have size 3
  }

  "query0 ref query1 ref query0" should "be cyclic error" in {

    val stage0 = StoredQueries()

    val stage1 = stage0.update(ItemCreated("0", "query0", None, Set("demo")))
    val stage2 = stage1.update(ItemCreated("1", "query1", None, Set("test")))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedClause("1", "query1", "must"))))

    val cycleClauseAdded = ClauseAdded("1", (100, NamedClause("0", "query0", "must")))
    an [Exception] should be thrownBy {     // Ensure a particular exception type is thrown
      stage3.update(cycleClauseAdded)
    }

    stage3.testCycleInDirectedGraph(cycleClauseAdded) should equal (Left("CycleInDirectedGraphError"))

    stage3.paths should have size 1
    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))
    val x :: xs = stage3.changes
    x should contain ("0")
    x should have size 1
    stage3.changes.flatten should have size 3
  }

  "dependencies" should "be empty when query0 remove ref query1" in {

    val stage0 = StoredQueries()

    val stage1 = stage0.update(ItemCreated("0", "query0", None, Set("demo")))
    val stage2 = stage1.update(ItemCreated("1", "query1", None, Set("test")))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedClause("1", "query1", "must"))))

    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))

    val stage4 = stage3.update(ClauseRemoved("0", Map(100 -> NamedClause("1", "query1", "must"))))

    stage4.paths should have size 0

    val x :: xs = stage4.changes
    x should contain ("0")
    x should have size 1
    stage4.changes.flatten should have size 4
    stage4.changes.flatten.count(_ == "0") should be (3)
  }

  "query1 changing" should "cascading update the consumer query0" in {

    val stage0 = StoredQueries()

    val stage1 = stage0.update(ItemCreated("0", "query0", None, Set("demo")))
    val stage2 = stage1.update(ItemCreated("1", "query1", None, Set("test")))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedClause("1", "query1", "must"))))

    val demoClause0 = MatchClause("goose owl", "dialogs", "AND", "should")
    val stage4 = stage3.update(ClauseAdded("1", (100, demoClause0)))

    assert(stage4.items("0").clauses.exists({
      case (100, NamedClause("1", "query1", "must", clauses)) if clauses.getOrElse(Map.empty).exists{ case (100, c) if c == demoClause0 => true } => true
      case _ => false
    }))
    val x :: xs = stage4.changes
    x should be (List("0", "1"))
    stage4.changes.flatten should have size 5
  }

  "named query" should "exist" in {
    val stage0 = StoredQueries()

    val stage1 = stage0.update(ItemCreated("0", "query0", None, Set("demo")))
    val stage2 = stage1.update(ItemCreated("1", "query1", None, Set("test")))

    val stage3 = stage2.update(ClauseAdded("1", (100, MatchClause("hello", "dialogs", "OR", "must"))))

    stage3.items("1").clauses should have size 1

    // query0
    val stage4 = stage3.update(ClauseAdded("0", (100, NamedClause("1", "query1", "must"))))

    stage4.items("0").clauses should have size 1

//    stage4.items("0").clauses(100) match {
//      case NamedClause(_, _, _, Some(clauses)) => clauses should have size 1
//      case _ => fail("there should only have a NamedClause")
//    }
  }

}
