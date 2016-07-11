package com.inu.cluster.storedquery.elasticsearch

import org.json4s._
import org.json4s.JsonDSL._
import com.inu.protocol.storedquery.messages._
import com.inu.protocol.media.CollectionJson.Template

/**
  * Created by henry on 6/9/16.
  */
object Percolator {
  def unapply(arg: StoredQuery): Option[(String,JObject)] = {
    val StoredQuery(id, title, clauses, tags) = arg
    val BoolQuery(query) = clauses.values

    val occurs = clauses.foldLeft(JObject()){ case (json, (cid, e)) =>

      val el: JObject = e.occurrence -> Set(Template(e).template ~~ ("href" -> s"/${e.shortName}/$cid"))

      json merge el
    }

    val item =
      ("href"   -> id) ~~
      ("data" -> Set(
        ("name" -> "title") ~~ ("value" -> title),
        ("name" -> "tags") ~~ ("value" -> tags.filterNot(_.isEmpty).mkString(" "))
      ))
      // ~~ ("links" -> JArray(Nil))

    val doc =
      ("title" -> title) ~~
      ("tags"  -> tags.filterNot(_.isEmpty)) ~~
      ("query" -> query) ~~
      ("item"  -> item) ~~
      ("occurs" -> occurs)
    Some((id, doc))
  }
}
