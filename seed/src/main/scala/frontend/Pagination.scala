package frontend

import org.elasticsearch.action.search.SearchResponse
import scala.language.implicitConversions
import scalaz._, Scalaz._
import scalaz.Ordering

object Pagination {

  implicit def extractHitsOfTotal(r: SearchResponse): Long = r.getHits.totalHits()
}

case class Pagination(size: Int, from: Int, totals: Long = 0)(implicit uri: spray.http.Uri) {

  private val next: Long = from + size
  private val previous = from - size

  val linkOfNext  = next ?|? totals match {
    case scalaz.Ordering.LT => Some(s"""{"prompt" : "Next", "rel" : "next", "href" : "${uri.withQuery(("from", s"$next"), ("size", s"$size"))}", "render" : "link"}""")
    case _ => None
  }

  val linkOfPrevious = previous ?|? 0 match {
    case Ordering.GT | Ordering.EQ => Some(s"""{"prompt" : "Previous", "rel" : "previous", "href" : "${uri.withQuery(("from", s"$previous"), ("size", s"$size"))}", "render" : "link"}""")
    case _ => None
  }

  lazy val links = Seq(linkOfNext, linkOfPrevious).flatten
}
