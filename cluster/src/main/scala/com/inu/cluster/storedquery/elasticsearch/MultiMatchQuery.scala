package com.inu.cluster.storedquery.elasticsearch

import org.json4s._
import org.json4s.JsonDSL._
import com.inu.protocol.storedquery.messages._

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
                  ("fields" -> fields) ~~
                  ("operator" -> o))
            )
          )
        )

    }
  }
}
