package com.inu.frontend.analysis

import spray.http.Uri

case class FurtherLinks(uri: Uri, storedQueryId: String) {

  def include = append("conditionSet", remove("must_not",remove("include", uri.query.toMap))).toSeq
  def exclude = append("include", remove("must_not",remove("conditionSet", uri.query.toMap))).toSeq
  def must_not = append("must_not", uri.query.toMap)
  lazy val deleteIncludable = remove("include", uri.query.toMap.-("must_not")).toSeq

  private def remove(key: String, map: Map[String, String]): Map[String, String] = {
    map.getOrElse(key, storedQueryId).replace(storedQueryId, "").trim match {
      case "" => map.-(key)
      case value => map.+(key -> value)
    }
  }
  //Option(uri.query.toMap.getOrElse(key, storedQueryId).replace(storedQueryId, "").trim).filter(_.nonEmpty).map(key -> _)

  private def append(key: String, map: Map[String, String]):  Map[String, String] =
    s"${map.getOrElse(key, "")} $storedQueryId".trim match {
      case "" => map.-(key)
      case str if str == s"$storedQueryId $storedQueryId" => map.+(key -> storedQueryId)
      case appended => map.+(key -> appended)
    }

  def action2: String = {
    uri.query.get("must_not") match {
      case Some(value) if value.contains(storedQueryId) =>
        s"""{"rel" : "action", "href" : "${uri.withQuery(remove("must_not", must_not).toSeq: _*)}", "query" : "must" }"""
      case _ =>
        s"""{"rel" : "action", "href" : "${uri.withQuery(must_not.toSeq: _*)}", "query" : "must_not" }"""
    }
  }

  def action1(state: String): String = {
    (state match {
      case "includable" => Some(deleteIncludable)
      case _ => None
    }).map { map =>
      s"""{"rel" : "remove", "href" : "${uri.withQuery(map: _*)}", "prompt" : "delete" }"""
    }.getOrElse("")
  }

  def action0(state: String): String = {
    (state match {
      case "excludable" => Some(exclude, "exclude", "none")
      case "includable" => Some(include, "include", "must")
      case _ => None
    }).map { case (map, prompt, query) =>
      s"""{"rel" : "action", "href" : "${uri.withQuery(map: _*)}", "prompt" : "$prompt", "query" : "$query" }"""
    }.getOrElse("")
  }
}