package com.inu.frontend.analysis

import com.inu.frontend.CollectionJsonSupport
import com.inu.frontend.directive.{StoredQueryDirectives, UserProfileDirectives}
import com.inu.frontend.elasticsearch.ImplicitConversions._
import com.inu.frontend.storedquery.PreviewRequest
import com.inu.protocol.media.CollectionJson.Template
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse}
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.index.query.WrapperQueryBuilder
import org.elasticsearch.search.SearchHit
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import shapeless._
import spray.http.StatusCodes._
import spray.http.Uri
import spray.http.Uri.Path
import spray.routing._

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

case class Condition(storedQueryId: String, title: String, query: String, state: String = "", conditions: Iterable[String] = Seq.empty, hits: Long = 0)

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

trait AnalysisRoute extends HttpService with CollectionJsonSupport with StoredQueryDirectives with UserProfileDirectives {

  implicit def executionContext: ExecutionContext

  def extractSourceItems(sr: SearchResponse): Directive1[List[(String, JValue)]] = {
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

        // /_analysis/cross?include=id
        val action = ("href" -> s"""${dropQuery.appendToValueOfKey("include")(id)}""".replaceFirst("""/source""", "")) ~~
                     ("rel" -> "action")

        (id,item merge (("links" -> JArray(action :: Nil)) ~~ ("href" -> s"${dropQuery.withPath(Uri.Path(s"/sapi/_query/template/$id")).withQuery()}")))
      }
      provide(items)
    }
  }

  def formatHits(qb: SearchRequestBuilder): Directive1[Map[String, Condition]] = {
    onSuccess(qb.execute().future).flatMap { res =>
      provide(res.getHits.map { h => h.id -> Condition(h) }.toMap)
    }
  }

  def conditionSet(map: Map[String, Condition]): Directive1[Map[String, WrapperQueryBuilder]] = {
    parameters('conditionSet.?).hflatMap {
      case ids :: HNil =>
        provide(map.filterKeys(ids.getOrElse("").contains).map { case (k, c) => k -> wrapperQuery(c.query) })
    }
  }

  def extractHighlights(r: SearchResponse): Directive1[List[JObject]] = {
    // TODO: 重复代码
    import com.inu.frontend.elasticsearch._
    parameters('conditionSet.?).flatMap { ids =>
      requestUri.flatMap { uri =>
        val extractor = """logs-(\d{4})\.(\d{2})\.(\d{2}).*\/([\w-]+$)""".r
        provide(r.getHits.map {
          case SearchHitHighlightFields(loc, fragments) =>
            val highlight = "highlight" -> fragments.map { case VttHighlightFragment(start, kw) => s"$start $kw" }
            val keywords = "keywords" -> fragments.flatMap { _.keywords.split("""\s+""") }.toSet.mkString(" ")
            val extractor(year, month, day, id) = loc
            val audioUrl = "audioUrl" -> s"$year$month$day/$id"
            // uri.toString().replaceFirst("\\/_.*$", "") 砍host:port/a/b/c 的path
            ("href" -> s"${ids.map{ v => uri.withQuery("_id" -> v)}.getOrElse(uri).withPath(Path(s"/sapi/$loc"))}") ~~ Template(Map(highlight, keywords, audioUrl, "id" -> s"$year$month$day")).template
        } toList)
      }
    }
  }

  def logs(map: Map[String, Condition]): Directive1[SearchResponse] = {
    conditionSet(map).flatMap { q =>
      parameter('size.as[Int] ? 10, 'from.as[Int] ? 0 ).hflatMap {
        case size :: from :: HNil => {

          //val noReturnQuery = boolQuery().mustNot(matchAllQuery())

          onSuccess(client.prepareSearch("logs-*")
              .setQuery(
                q.values.foldLeft(boolQuery())(_ must _)
              )
              .setSize(size).setFrom(from)
              .addField("vtt")
              .setHighlighterRequireFieldMatch(true)
              .setHighlighterNumOfFragments(0)
              .setHighlighterPreTags("<em>")
              .setHighlighterPostTags("</em>")
              .addHighlightedField("agent*")
              .addHighlightedField("customer*")
              .addHighlightedField("dialogs").execute().future).flatMap { res =>
                provide(res)
              }
        }
        case _ => reject
      }
    }
  }

  def set(map: Map[String, Condition]): Directive1[JObject] = {
    conditionSet(map).flatMap { q =>
      onSuccess(client.prepareSearch("logs-*")
        .setQuery(q.values.foldLeft(boolQuery())(_ must _))
        .setSize(0)
        .execute().future).flatMap { res =>
          requestUri.flatMap { uri =>

            // TODO: 生成用户可以查询的type进行分类
            // val logsLink = """{ "rel" : "logs", "render" : "grid", "name": "%s", "href" : "%s"}"""
            // typ.map { _.split("""(\s+|,)""").map { t => logsLink.format(t, uri.withPath(uri.path / "logs").withExistQuery(("type", t))) }.toList }
            //            .getOrElse(List(logsLink.format("*", uri.withPath(uri.path / "logs"))))

            provide(
              Template(Map("title" -> "set", "state" -> "set", "hits" -> res.getHits.totalHits)).template ~~
                ("links" -> JArray(("rel" -> "logs") ~~ ("render" -> "grid") ~~ ("name" -> "*") ~~ ("href" -> s"${uri.withPath(uri.path / "logs")}") :: Nil))
            )
          }
      }
    }
  }

  def exclude(map: Map[String, Condition]): Directive1[List[JObject]] = {
      parameters('conditionSet.?).flatMap { exclude =>
        conditionSet(map).flatMap { set =>
          requestUri.flatMap { uri =>
            onSuccess(Future.traverse(map.filterKeys(exclude.getOrElse("").contains)) {
              case (k, c) =>
                val links = FurtherLinks(uri, k)
                val q = (set - k).values.foldLeft(boolQuery())(_ must _)
                client.prepareSearch("logs-*").setQuery(q).setSize(0).execute().future.map { res =>
                  Template(Map("title" -> c.title, "state" -> "excludable", "hits" -> res.getHits.totalHits)).template ~~
                    ("links" -> Set(
                      parse(links.action0("excludable")),
                      parse(links.action1("excludable"))
                    ))
                }
            }).flatMap { items =>
              provide(items.toList)
            }
          }
        }
      }
  }

  def include(map: Map[String, Condition]): Directive1[List[JObject]] = {
    parameters('include.?).flatMap { include =>
      conditionSet(map).flatMap { set =>
        requestUri.flatMap { uri =>
          onSuccess(Future.traverse(map.filterKeys(include.getOrElse("").contains)) {
            case (k, c) =>
              val links = FurtherLinks(uri, k)
              val q = (set + (k -> wrapperQuery(c.query))).values.foldLeft(boolQuery())(_ must _)
              client.prepareSearch("logs-*").setQuery(q).setSize(0).execute().future.map { res =>
                Template(Map("title" -> c.title, "state" -> "includable", "hits" -> res.getHits.totalHits)).template ~~
                  ("links" -> Set(
                    parse(links.action0("includable")),
                    parse(links.action1("includable"))
                  ))
              }
          }).flatMap { items =>
            provide(items.toList)
          }
        }
      }
    }
  }

  //def graph0

  lazy val `_analysis`: Route =
    get {
      requestUri { uri =>
        import com.inu.frontend.UriImplicitConversions._
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
              conditions { searchSq =>
                formatHits(searchSq) { conditionsMap =>
                  set(conditionsMap) { set =>
                    include(conditionsMap) { includes =>
                      exclude(conditionsMap) { excludes =>
                        val links = JField("links", JArray(("href" -> s"${uri.withPath(uri.path / "source")}") ~~ ("rel" -> "source"):: Nil))
                        complete(OK, JField("href", JString(s"$uri")) :: links :: JField("items", JArray(set :: includes ++ excludes)) :: Nil)
                      }
                    }
                  }
                }
              }
            } ~
            path("source") {
              prepareSearchPercolator { srb =>
                onSuccess(srb.execute().future) { sr =>
                  pagination(sr)(uri) { p =>
                    extractSourceItems(sr) { items =>
                      implicit val withoutSearchParamsUri = uri.drop("q", "tags", "size", "from")
                      val href = JField("href", JString(s"$withoutSearchParamsUri"))
                      val links = JField("links", JArray(p.links))

                      val searchParams = Template(Map("from" -> 0, "size" -> 10, "q" -> "", "tags" -> ""),
                        Map("size" -> "size of displayed items",
                            "from" -> "items display from",
                            "q"    -> "search title or any terms",
                            "tags" -> "")).template

                      val queries = JField("queries", JArray(("href" -> s"$withoutSearchParamsUri") ~~ ("rel" -> "search") ~~ searchParams :: Nil))
                      complete(OK, href :: queries :: links :: JField("items", JArray(items.map { case(_,item) => item })) :: Nil)
                    }
                  }
                }
              }
            } ~
            path("logs") {
              conditions { searchSq =>
                formatHits(searchSq) { conditionsMap =>
                  logs(conditionsMap) { res =>
                    extractHighlights(res) { items =>
                      pagination(res)(uri) { p =>
                        val links = JField("links", JArray(p.links))
                        val href = JField("href", JString(s"$uri"))
                        complete(OK, href :: links :: JField("items", JArray(items)) :: Nil)
                      }
                    }
                  }
                }
              }
            } ~
            path("graph0") {

              complete(OK)
            }
          }
        }
      }
    }
}
