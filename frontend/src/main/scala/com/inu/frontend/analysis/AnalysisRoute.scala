package com.inu.frontend.analysis

import com.inu.frontend.CollectionJsonSupport
import com.inu.frontend.directive.StoredQueryDirectives
import spray.routing._
import spray.http.StatusCodes._
import com.inu.frontend.elasticsearch.ImplicitConversions._
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse}
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.search.SearchHit
import org.json4s.DefaultFormats
import org.json4s._
import org.json4s.native.JsonMethods._
import shapeless.{::, HNil}
import spray.http.Uri
import org.json4s.JsonDSL._
import scala.collection.JavaConversions._

case class Condition(storedQueryId: String, title: String, query: String, state: String = "", conditions: Iterable[String] = Seq.empty, hits: Long = 0)

case class ConditionSet(conditions: Seq[String]) {

  def exclude(storedQueryId: String): Condition =
    Condition(storedQueryId, storedQueryId, "", "excludable", conditions.filterNot(_ == storedQueryId))

  def include(storedQueryId: String): Condition =
    Condition(storedQueryId, storedQueryId, "", "includable", conditions.+:(storedQueryId))

  def all = Condition("set", "set", "", "set", conditions)
}

object Condition {
  import org.json4s.native.JsonMethods._
  def apply(h: SearchHit): Condition = {
    implicit val formats = DefaultFormats
    val json = parse(h.getSourceAsString)
    val JString(title) = json \ "title"
    val query = compact(render(json \ "query"))
    Condition(h.id(), title , query, "")
  }
}

trait AnalysisRoute extends HttpService with CollectionJsonSupport with StoredQueryDirectives {

  def formatHits(qb: SearchRequestBuilder): Directive1[Map[String, Condition]] = {
    onSuccess(qb.execute().future).flatMap { res =>
      provide(res.getHits.map { h => h.id -> Condition(h) }.toMap)
    }
  }

  def format2(percolators: Map[String, Condition]) = {
    parameters('conditionSet.?, 'include.?).hflatMap {
      case conditionSet :: include :: HNil =>
        //ConditionSet(conditionSet)
        provide("")
    }
  }

  def extractItems(sr: SearchResponse): Directive1[List[(String, JValue)]] = {
    requestUri.flatMap { uri =>
      import com.inu.frontend.UriImplicitConversions._
      val dropQuery = uri.drop("q", "tags", "size", "from")

      val hits = parse(s"$sr") \ "hits" \ "hits" match {
        case o: JObject => o :: Nil
        case JArray(xs) => xs
        case _ => Nil
      }
      val items = hits.map { h =>
        val JString(id) = h \ "_id"
        val item = h \ "_source" \ "item"

        // http://host:port/_analysis/cross?include=id
        val action = ("href" -> s"""${dropQuery.appendToValueOfKey("include")(id)}""".replaceFirst("""/source""", "")) ~~
                     ("rel" -> "action")

        (id,item merge (("links" -> JArray(action :: Nil)) ~~ ("href" -> s"${dropQuery.withPath(Uri.Path(s"/sapi/_query/template/$id")).withQuery()}")))
      }
      provide(items)
    }
  }

  lazy val `_analysis`: Route =
    get {
      requestUri { uri =>
        import com.inu.frontend.UriImplicitConversions._
        implicit val withoutSearchParamsUri = uri.drop("q", "tags", "size", "from")
        pathPrefix( "_analysis" ) {
          pathEnd {
            val body =  s"""{
                            |  "collection" : {
                            |    "version" : "1.0",
                            |    "href" : "$uri",
                            |
                            |    "queries" : [
                            |      { "href" : "$uri/cross",
                            |        "rel" : "search",
                            |        "data" : [
                            |          { "name" : "conditionSet", "value" : "" },
                            |          { "name" : "include", "value" : "" }
                            |        ]
                            |      }
                            |    ]
                            |  }
                            |}
          """.stripMargin
            complete(OK, body)
          } ~
          pathPrefix("cross") {
            pathEnd {
              prepareSearchPercolator3 { query =>
                formatHits(query) { all =>
                  complete(OK, s"${all}")
                }
              }
            } ~
            path("source") {
              prepareSearchPercolator { srb =>
                onSuccess(srb.execute().future) { sr =>
                  pagination(sr)(uri) { p =>
                    extractItems(sr) { items =>
                      val href = JField("href", JString(s"$withoutSearchParamsUri"))
                      val links = JField("links", JArray(p.links))
                      complete(OK, href :: links :: JField("items", JArray(items.map { case(_,item) => item })) :: Nil)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
}
