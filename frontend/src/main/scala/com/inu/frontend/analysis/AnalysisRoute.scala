package com.inu.frontend.analysis

import com.inu.frontend.CollectionJsonSupport
import com.inu.frontend.elasticsearch.ImplicitConversions._
import com.inu.protocol.media.CollectionJson.Template
import org.elasticsearch.common.xcontent.{ToXContent, XContentFactory, XContentType}
import org.elasticsearch.search.aggregations.bucket.filters.Filters
import org.json4s.JsonDSL._
import org.json4s._
import spray.http.StatusCodes._
import spray.routing._


trait AnalysisRoute extends HttpService with CollectionJsonSupport with CrossDirectives {

  //val builder =  XContentFactory.contentBuilder(XContentType.JSON);
  lazy val graph =
    respondWithMediaType(spray.http.MediaTypes.`application/json`) {
      datasourceAggregation { agg0 =>
          path("graph0") {
            conditionSetAggregation(agg0) { agg1 =>
//              println("conditionSetAggregation")
//              builder.startObject()
//              agg1.toXContent(builder, ToXContent.EMPTY_PARAMS)
//              builder.endObject()
//              println(builder.string)
              datasourceBuckets(agg1) { buckets =>

                val arr = buckets.foldLeft(List.empty[JObject]) { (acc, bucket) =>
                  // nested
                  def format(b: Filters.Bucket, defaultKey: Option[String]): JArray = {
                    JArray(JString(defaultKey.getOrElse(b.getKeyAsString)) :: JInt(b.getDocCount) :: Nil)
                  }
                  val values = getBuckets(bucket, "individual") match {
                    case Nil  => format(bucket, Some("*")) :: Nil
                    case list => list.map(format(_, None))
                  }
                  ("key" -> bucket.getKeyAsString) ~~ ("values" -> values) :: acc
                }
                complete(OK, JArray(arr))
              }
            }
          } ~
          path("graph1") {
            includeAggregation(agg0) { agg1 =>
              datasourceBuckets(agg1) { buckets =>
                val arr = buckets.foldLeft(List.empty[JObject]) { (acc, bucket) =>
                  def format(b: Filters.Bucket,defaultKey: Option[String] = None): JObject = ("label" -> defaultKey.getOrElse(b.getKeyAsString)) ~~ ("y" -> b.getDocCount.toInt)
                  val zero = List(format(bucket, Some("*")) ~~ ("x" -> 0))
                  val values = getBuckets(bucket, "cross") match {
                    case Nil  => zero
                    case list => list.foldLeft(zero){ (acc, el) => format(el) ~~ ("x" -> acc.size) :: acc }
                  }
                  ("key" -> bucket.getKeyAsString) ~~ ("values" -> values.reverse) :: acc
                }
                complete(OK, JArray(arr))
              }
            }
          }
      }
    }

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
              `conditionSet+include` { searchSq =>
                formatHits(searchSq) { conditionsMap =>
                  set(conditionsMap) { set =>
                    include(conditionsMap) { includes =>
                      exclude(conditionsMap) { excludes =>
                        val links = JField("links", JArray(
                          ("href" -> s"${uri.withPath(uri.path / "source")}") ~~ ("rel" -> "source") ::
                          ("href" -> s"${uri.withPath(uri.path / "graph0")}") ~~ ("rel" -> "graph") ~~ ("render" -> "bar") ::
                          ("href" -> s"${uri.withPath(uri.path / "graph1")}") ~~ ("rel" -> "graph") ~~ ("render" -> "line") :: Nil))
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
                      tags { allTags =>
                        implicit val withoutSearchParamsUri = uri.drop("q", "tags", "size", "from")
                        val href = JField("href", JString(s"$withoutSearchParamsUri"))
                        val links = JField("links", JArray(p.links))

                        val searchParams = Template(Map("from" -> 0, "size" -> 10, "q" -> "", "tags" -> ""),
                          Map("size" -> "size of displayed items",
                              "from" -> "items display from",
                              "q"    -> "search title or any terms",
                              "tags" -> allTags)).template

                        val queries = JField("queries", JArray(("href" -> s"$withoutSearchParamsUri") ~~ ("rel" -> "search") ~~ searchParams :: Nil))
                        complete(OK, href :: queries :: links :: JField("items", JArray(items.map { case(_,item) => item })) :: Nil)
                      }
                    }
                  }
                }
              }
            } ~
            path("logs") {
              `conditionSet+include` { searchSq =>
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
            graph
          }
        }
      }
    }
}
