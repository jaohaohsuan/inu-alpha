package com.inu.cluster.storedquery.elasticsearch

import com.inu.cluster.storedquery.algorithm.ListOfListCombination
import org.json4s._
import org.json4s.JsonDSL._
import com.inu.protocol.storedquery.messages._
import org.json4s.JsonAST.JObject

object SynonymBoolQuery {
  def unapply(arg: MatchClause): Option[JValue] = {
    val MatchClause(q,f,o,occur) = arg
    f.split("""[\s,]+""").toList match {
      case Nil => Some(JNothing)
      case fields =>
        """\/""".r.findFirstMatchIn(q) match {
          case Some(_) =>
            val matches: Seq[JObject] = ListOfListCombination.generator(q.split("""[\s,]+""").map(_.split("\\/").toList).toList).map { el =>
              JObject("multi_match" ->
                ("query"    -> el.mkString(" ")) ~~
                ("fields"   -> fields) ~~
                ("operator" -> o)
              )
            }

            Some("bool" -> (
                  ("minimum_should_match" -> 1) ~~
                  (occur                  -> Set("bool" -> (("minimum_should_match" -> 1) ~~ ("should" -> matches))))
              )
            )
          case _ => None
        }
    }
  }
}

object MultiMatchQuery {
  def unapply(arg: MatchClause): Option[JValue] = {
    val MatchClause(q,f,o,occur) = arg
    f.split("""[\s,]+""").toList match {
      case Nil => Some(JNothing)
      case fields =>
        Some("bool" ->
          (
            ("minimum_should_match" -> 1) ~~
            (occur -> Set(
              "multi_match" ->
                ("query"    -> q) ~~
                ("fields"   -> fields) ~~
                ("operator" -> o)
              )
            )
          )
        )
    }
  }
}
