package com.inu.frontend.storedquery

import akka.actor.Props
import akka.pattern._
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import com.inu.protocol.media.CollectionJson.Template
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse}
import org.json4s.{JObject, JValue}
import org.json4s.JsonAST.{JArray, JField, JString}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.{Path,Query}
import akka.http.scaladsl.server._

import scala.collection.JavaConversions._


case class PreviewStatus(count: Long, query: JValue)

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
    // TODO: 重复代码
    import com.inu.frontend.elasticsearch._
    extractUri.map { uri => {
        val extractor = """logs-(\d{4})\.(\d{2})\.(\d{2}).*\/([\w-]+$)""".r
        r.getHits.map {
          case hit@SearchHitHighlightFields(loc, fragments) =>
            val highlight = "highlight" -> fragments.map { case VttHighlightFragment(start, kw) => s"$start $kw" }
            val keywords = "keywords" -> fragments.flatMap { _.keywords.split("""\s+""") }.toSet.mkString(" ")
            val extractor(year, month, day, id) = loc
            val audioUrl = "audioUrl" -> s"$year$month$day/$id"
            // uri.toString().replaceFirst("\\/_.*$", "") 砍host:port/a/b/c 的path
            ("href" -> s"${uri.withQuery(Query("_id" -> ids)).withPath(Path(s"/sapi/$loc"))}") ~~ Template(Map(highlight, keywords, audioUrl, "id" -> s"$year$month$day") ++ hit.getFields.toMap.map{ case (k, v) => k -> v.value }).template
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
        extractUri { uri =>
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
