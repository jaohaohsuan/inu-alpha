package com.inu.frontend.storedquery

import akka.actor.Props
import akka.pattern._
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import com.inu.protocol.media.CollectionJson.Template
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse}
import org.json4s.{JObject, JValue}
import org.json4s.JsonAST.{JArray, JField, JString}
import spray.http.StatusCodes._
import shapeless._
import spray.http.Uri.Path

import scala.collection.JavaConversions._
import spray.routing._

case class PreviewStatus(count: Long, query: JValue)
case class LogItem(highlight: List[String], keywords: String)

object PreviewRequest {
  def props(s: SearchRequestBuilder, storedQueryId: String)(implicit ctx: RequestContext) = {
    Props(classOf[PreviewRequest], ctx, s, storedQueryId)
  }
}

case class PreviewRequest(ctx: RequestContext, s: SearchRequestBuilder, storedQueryId: String) extends PerRequest with CollectionJsonSupport{

  import com.inu.frontend.UriImplicitConversions._
  import com.inu.frontend.elasticsearch.ImplicitConversions._
  import context.dispatcher
  import org.json4s.JsonDSL._

  def extractHighlights(r: SearchResponse)(ids: String): Directive1[List[JObject]] = {
    import com.inu.frontend.elasticsearch._

    requestUri.hmap {
      case uri :: HNil => {
        r.getHits.map {
          case SearchHitHighlightFields(loc, fragments) =>
            val highlight = fragments.map { case VttHighlightFragment(start, kw) => s"$start $kw" }
            val keywords = fragments.flatMap { _.keywords.split("""\s+""") }.toSet.mkString(" ")
            // uri.toString().replaceFirst("\\/_.*$", "")
            ("href" -> s"${uri.withPath(Path(s"/sapi/$loc")).withQuery(("_id", ids))}") ~~ Template(LogItem(highlight, keywords)).template
        } toList
      }
    }
  }

  //val storedQueryQuery = compact(render(item \ "query"))
  //println(s"$s")

  s.execute().future pipeTo self


  def processResult: Receive = {
    case res: SearchResponse =>
      response {
        requestUri { uri =>
          pagination(res)(uri) { p =>
            extractHighlights(res)(storedQueryId) { items =>
              val status = ("rel" -> "status") ~~ ("href" -> s"${uri / "status"}")
              val links = JField("links", JArray(status :: p.links))
              val href = JField("href", JString(s"$uri"))

              complete(OK, href :: links :: JField("items", JArray(items)) :: Nil)
            }
          }
        }
      }
  }
}
