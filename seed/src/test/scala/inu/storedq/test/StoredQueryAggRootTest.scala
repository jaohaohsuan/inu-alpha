package inu.storedq.test

import domain.StoredQueryRepoAggRoot.StoredQueries2
import domain.{ClauseAdded, ClauseRemoved, ItemCreated2}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.Matchers._
import org.scalatest._
import protocol.storedQuery.{NamedBoolClause, StoredQuery}

/**
  * Created by henry on 4/29/16.
  */
class StoredQueryAggRootTest extends FlatSpec with Matchers {

  "query0" should "be added" in {

    val stage0 = StoredQueries2()

    val stage2 = stage0.update(ItemCreated2("123", None, "query0", "demo"::Nil))

    assert(stage2.items.exists(_ == ("123", StoredQuery("123", title = "query0", Map.empty, Set("demo")))))
    stage2.paths should be (empty)
  }

  "query0 ref query1" should "be no cyclic error" in {

    val stage0 = StoredQueries2()

    val stage1 = stage0.update(ItemCreated2("0", None, "query0", "demo"::Nil))
    val stage2 = stage1.update(ItemCreated2("1", None, "query1", "test"::Nil))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedBoolClause("1", "query1", "must"))))
    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))
  }

  "query0 ref query1 ref query0" should "be cyclic error" in {

    val stage0 = StoredQueries2()

    val stage1 = stage0.update(ItemCreated2("0", None, "query0", "demo"::Nil))
    val stage2 = stage1.update(ItemCreated2("1", None, "query1", "test"::Nil))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedBoolClause("1", "query1", "must"))))
    val stage4 = stage3.update(ClauseAdded("1", (100, NamedBoolClause("0", "query0", "must"))))

    stage4.paths should have size 1
    assert(stage4.paths.exists(_ == (("0", "1") -> 100)))
  }

  "dependencies" should "be empty when query0 remove ref query1" in {

    val stage0 = StoredQueries2()

    val stage1 = stage0.update(ItemCreated2("0", None, "query0", "demo"::Nil))
    val stage2 = stage1.update(ItemCreated2("1", None, "query1", "test"::Nil))

    val stage3 = stage2.update(ClauseAdded("0", (100, NamedBoolClause("1", "query1", "must"))))

    assert(stage3.paths.exists(_ == (("0", "1") -> 100)))

    val stage4 = stage3.update(ClauseRemoved("0", Map(100 -> NamedBoolClause("1", "query1", "must"))))

    stage4.paths should have size 0
  }



}
