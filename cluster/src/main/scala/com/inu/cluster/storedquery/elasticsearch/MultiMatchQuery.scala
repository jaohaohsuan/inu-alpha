package com.inu.cluster.storedquery.elasticsearch

import org.json4s._
import org.json4s.JsonDSL._
import com.inu.protocol.storedquery.messages._

object MultiMatchQuery {
  def unapply(arg: MatchClause): Option[JValue] = {
    val MatchClause(q,f,o,occur) = arg
    Some("bool" ->
      (occur -> Set(
        "multi_match" ->
          ("query"    -> q) ~~
            ("fields" -> ("""\w+""".r findAllIn f).toSet) ~~
            ("operator" -> o)
      ))
    )
  }
}
