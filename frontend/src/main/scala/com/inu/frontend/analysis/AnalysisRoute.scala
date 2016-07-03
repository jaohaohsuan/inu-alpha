package com.inu.frontend.analysis

import com.inu.frontend.CollectionJsonSupport
import com.inu.frontend.elasticsearch.ImplicitConversions._
import com.inu.protocol.media.CollectionJson.Template
import org.json4s.JsonDSL._
import org.json4s._
import spray.http.StatusCodes._
import spray.routing._


trait AnalysisRoute extends HttpService with CollectionJsonSupport with CrossDirectives {

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
                        val links = JField("links", JArray(
                          ("href" -> s"${uri.withPath(uri.path / "source")}") ~~ ("rel" -> "source") ::
                          ("href" -> s"${uri.withPath(uri.path / "graph0")}") ~~ ("rel" -> "graph")  ::
                          ("href" -> s"${uri.withPath(uri.path / "graph1")}") ~~ ("rel" -> "graph")  :: Nil))
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
              conditions { searchSq =>
                formatHits(searchSq) { conditionsMap =>
                  parameters('conditionSet.?) { ids =>
                    datasourceAggregation { dsa =>
                      storedQueryAggregation(dsa) { sqa =>
                        val f = client.prepareSearch("logs-*").addAggregation(sqa).execute().future.map(_.getAggregations)
                        onSuccess(f) { aggs =>
                          complete(OK, s"$aggs")
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
    }
}
