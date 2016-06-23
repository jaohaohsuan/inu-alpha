package com.inu.frontend.analysis

import spray.http.Uri

case class FurtherLinks(uri: Uri, storedQueryId: String) {

  def include = append("conditionSet", remove("include", uri.query.toMap)).toSeq
  def exclude = append("include", remove("conditionSet", uri.query.toMap)).toSeq
  def deleteIncludable = remove("include", uri.query.toMap).toSeq

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
      case appended =>  map.+(key -> appended)
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
      case "excludable" => Some(exclude, "exclude")
      case "includable" => Some(include, "include")
      case _ => None
    }).map { case (map, prompt) =>
      s"""{"rel" : "action", "href" : "${uri.withQuery(map: _*)}", "prompt" : "$prompt" }"""
    }.getOrElse("")
  }
}