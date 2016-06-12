package com.inu.cluster.storedquery.elasticsearch

import org.json4s._
import org.json4s.JsonDSL._
import com.inu.protocol.storedquery.messages._

/**
  * Created by henry on 6/9/16.
  */
object Percolator {
  def unapply(arg: StoredQuery): Option[(String,JObject)] = {
    val StoredQuery(id, title, clauses, tags) = arg
    val BoolQuery(query) = clauses.values
    val item =
      ("href"   -> id) ~~
      ("data" -> Set(
        ("name" -> "title") ~~ ("value" -> title),
        ("name" -> "tags") ~~ ("value" -> tags.mkString(" "))
      ))

    val doc =
      ("title" -> title) ~~
      ("tags"  -> tags) ~~
      ("query" -> query) ~~
      ("item"  -> item)
    Some((id, doc))
  }
}
