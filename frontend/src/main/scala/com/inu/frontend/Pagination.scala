package com.inu.frontend

import org.elasticsearch.action.search.SearchResponse
import org.json4s.JValue
import org.json4s.JsonAST.JObject
import akka.http.scaladsl.model.Uri

import scala.language.implicitConversions
import scalaz._
import Scalaz._
import scalaz.Ordering
import org.json4s.JsonDSL._

object Pagination {

  implicit def extractHitsOfTotal(r: SearchResponse): Long = r.getHits.totalHits()
}

case class Pagination(size: Int, from: Int, totals: Long = 0)(implicit uri: akka.http.scaladsl.model.Uri) {

  import UriImplicitConversions._

  private val next: Long = from + size
  private val previous = from - size

  lazy val linkOfNext  = next ?|? totals match {
    case scalaz.Ordering.LT        =>
      Some(
        ("prompt" -> "Next") ~~
        ("rel"    -> "next") ~~
        ("href"   -> s"${uri.withExistQuery(("from", s"$next"), ("size", s"$size"))}") ~~
        ("render" -> "link"))
    case _ => None
  }

  lazy val linkOfPrevious = previous ?|? 0 match {
    case Ordering.GT | Ordering.EQ =>
      Some(
        ("prompt" -> "Previous") ~~
        ("rel"    -> "previous") ~~
        ("href"   -> s"${uri.withExistQuery(("from", s"$previous"), ("size", s"$size"))}") ~~
        ("render" -> "link"))
    case _ => None
  }

  lazy val links: List[JValue] = List(linkOfNext, linkOfPrevious).flatten
}