package inu.storedq.test

import domain.StoredQueryRepoAggRoot.StoredQueries2
import domain.{ClauseAdded, ClauseRemoved, ItemCreated2}
import org.scalatest.{FlatSpec, Matchers}
import protocol.storedQuery.{MatchBoolClause, NamedBoolClause, StoredQuery}

/**
  * Created by henry on 4/29/16.
  */
class StoredQueryAggRootTest extends FlatSpec with Matchers {

  "query0" should "be added" in {

    val stage0 = StoredQueries2()

    val stage2 = stage0.update(ItemCreated2("123", None, "query0", "demo"::Nil))

    assert(stage2.items.exists(_ == ("123", StoredQuery("123", title = "query0", Map.empty, Set("demo")))))
    stage2.paths should be (empty)
    val x :: xs = stage2.changes
    x should contain ("123")
  }

  "query0 ref query1" should "be no cyclic error" in {

    val stage0 = StoredQueries2()

    val stage1 = stage0.update(ItemCreated2("0", None, "query0", "demo"::Nil))
    val stage2 = stage1.update(ItemCreated2("1", None, "query1", "test"::Nil))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedBoolClause("1", "query1", "must"))))
    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))
    val x :: xs = stage3.changes
    x should contain ("0")
    x should have size 1
    stage3.changes.flatten should have size 3
  }

  "query0 ref query1 ref query0" should "be cyclic error" in {

    val stage0 = StoredQueries2()

    val stage1 = stage0.update(ItemCreated2("0", None, "query0", "demo"::Nil))
    val stage2 = stage1.update(ItemCreated2("1", None, "query1", "test"::Nil))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedBoolClause("1", "query1", "must"))))

    an [Exception] should be thrownBy {     // Ensure a particular exception type is thrown
      stage3.update(ClauseAdded("1", (100, NamedBoolClause("0", "query0", "must"))))
    }

    stage3.paths should have size 1
    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))
    val x :: xs = stage3.changes
    x should contain ("0")
    x should have size 1
    stage3.changes.flatten should have size 3
  }

  "dependencies" should "be empty when query0 remove ref query1" in {

    val stage0 = StoredQueries2()

    val stage1 = stage0.update(ItemCreated2("0", None, "query0", "demo"::Nil))
    val stage2 = stage1.update(ItemCreated2("1", None, "query1", "test"::Nil))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedBoolClause("1", "query1", "must"))))

    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))

    val stage4 = stage3.update(ClauseRemoved("0", Map(100 -> NamedBoolClause("1", "query1", "must"))))

    stage4.paths should have size 0

    val x :: xs = stage4.changes
    x should contain ("0")
    x should have size 1
    stage4.changes.flatten should have size 4
    stage4.changes.flatten.count(_ == "0") should be (3)
  }

  "query1 changing" should "cascading update the consumer query0" in {

    val stage0 = StoredQueries2()

    val stage1 = stage0.update(ItemCreated2("0", None, "query0", "demo"::Nil))
    val stage2 = stage1.update(ItemCreated2("1", None, "query1", "test"::Nil))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedBoolClause("1", "query1", "must"))))

    val demoClause0 = MatchBoolClause("goose owl", "dialogs", "AND", "should")
    val stage4 = stage3.update(ClauseAdded("1", (100, demoClause0)))

    assert(stage4.items("0").clauses.exists({
      case (100, NamedBoolClause("1", "query1", "must", clauses)) if clauses.exists{ case (100, c) if c == demoClause0 => true } => true
      case _ => false
    }))
    val x :: xs = stage4.changes
    x should be (List("0", "1"))
    stage4.changes.flatten should have size 5
  }

}
