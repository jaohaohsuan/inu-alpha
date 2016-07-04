package com.inu.frontend.analysis

import com.inu.frontend.directive.{StoredQueryDirectives, UserProfileDirectives}
import com.inu.protocol.media.CollectionJson.Template
import org.elasticsearch.action.search.{SearchRequestBuilder, SearchResponse}
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.aggregations.{Aggregation, AggregationBuilders}
import org.elasticsearch.search.aggregations.bucket.filters.{Filters, FiltersAggregationBuilder}
import org.json4s.JsonAST.{JArray, JString}
import org.json4s._
import org.json4s.native.JsonMethods._
import shapeless.{::, HNil}
import spray.http.StatusCodes._
import spray.http.Uri
import spray.http.Uri.Path
import spray.routing._

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

trait CrossDirectives extends Directives with StoredQueryDirectives with UserProfileDirectives {

  implicit def executionContext: ExecutionContext

  import com.inu.frontend.elasticsearch.ImplicitConversions._
  import scala.collection.JavaConversions._
  import org.json4s.JsonDSL._

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

  def conditionSet(map: Map[String, Condition]): Directive1[Map[String, JValue]] = {
    parameters('conditionSet.?).hflatMap {
      case ids :: HNil =>
        provide(map.filterKeys(ids.getOrElse("").contains).map { case (k,v) => k -> parse(v.query)})
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
    conditionSet(map).flatMap { clauses =>
      queryWithUserFilter(clauses.values.toList).flatMap { q =>
        parameter('size.as[Int] ? 10, 'from.as[Int] ? 0).hflatMap {
          case size :: from :: HNil => {
            //val noReturnQuery = boolQuery().mustNot(matchAllQuery())
            onSuccess(
              client.prepareSearch("logs-*")
                .setQuery(q)
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
  }

  def queryWithUserFilter(clauses: List[JValue]): Directive1[QueryBuilder] = {
    userFilter.flatMap { query =>
      import org.json4s.JsonDSL._
      val dd: JObject = "indices" -> ("query" -> ("bool" -> ("must" -> clauses)))
      val withUserFilterQuery = query merge dd
      val JArray(xs) = withUserFilterQuery \ "indices" \ "indices"
      val indices = xs.collect { case JString(s) => s }
      provide(indicesQuery(wrapperQuery(compact(render(withUserFilterQuery \ "indices" \ "query"))), indices: _*).noMatchQuery("none"))
    }
  }

  def set(map: Map[String, Condition]): Directive1[JObject] = {
    conditionSet(map).flatMap { q =>
      queryWithUserFilter(q.values.toList).flatMap { qb =>
        onSuccess(client.prepareSearch("logs-*")
          .setQuery(qb)
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
  }

  def exclude(map: Map[String, Condition]): Directive1[List[JObject]] = {
    parameters('conditionSet.?).flatMap { exclude =>
      conditionSet(map).flatMap { set =>
        requestUri.flatMap { uri =>
          userFilter.flatMap { query =>
            val f = for {
              items <- Future.traverse(map.filterKeys(exclude.getOrElse("").contains)) {
                case (k, c) =>
                  val links = FurtherLinks(uri, k)
                  val dd: JObject = "indices" -> ("query" -> ("bool" -> ("must" -> (set - k).values.toList)))
                  val withUserFilterQuery = query merge dd
                  val JArray(xs) = withUserFilterQuery \ "indices" \ "indices"
                  val indices = xs.collect { case JString(s) => s}
                  val q = indicesQuery(wrapperQuery(compact(render(withUserFilterQuery \ "indices" \ "query"))), indices: _*).noMatchQuery("none")
                  client.prepareSearch("logs-*").setQuery(q).setSize(0).execute().future.map { res =>
                    Template(Map("title" -> c.title, "state" -> "excludable", "hits" -> res.getHits.totalHits)).template ~~
                      ("links" -> Set(
                        parse(links.action0("excludable")),
                        parse(links.action1("excludable"))
                      ))
                  }
              }
            } yield items
            onSuccess(f).flatMap { items =>
              provide(items.toList)
            }
          }
        }
      }
    }
  }

  def include(map: Map[String, Condition]): Directive1[List[JObject]] = {
    parameters('include.?).flatMap { include =>
      conditionSet(map).flatMap { set =>
        requestUri.flatMap { uri =>
          userFilter.flatMap { query =>
            val f = for {
              items <- Future.traverse(map.filterKeys(include.getOrElse("").contains)) {
                case (k, c) =>
                  val links = FurtherLinks(uri, k)
                  val dd: JObject = "indices" -> ("query" -> ("bool" -> ("must" -> (set + (k -> parse(c.query))).values.toList)))
                  val withUserFilterQuery = query merge dd
                  val JArray(xs) = withUserFilterQuery \ "indices" \ "indices"
                  val indices = xs.collect { case JString(s) => s}
                  val q = indicesQuery(wrapperQuery(compact(render(withUserFilterQuery \ "indices" \ "query"))), indices: _*).noMatchQuery("none")
                  client.prepareSearch("logs-*").setQuery(q).setSize(0).execute().future.map { res =>
                    Template(Map("title" -> c.title, "state" -> "includable", "hits" -> res.getHits.totalHits)).template ~~
                      ("links" -> Set(
                        parse(links.action0("includable")),
                        parse(links.action1("includable"))
                      ))
                  }
              }} yield items
            onSuccess(f).flatMap { items =>
              provide(items.toList)
            }
          }
        }
      }
    }
  }

  def datasourceAggregation: Directive1[FiltersAggregationBuilder] = {
    dataSourceFilters.flatMap { filters =>
      val ds = filters.foldLeft(AggregationBuilders.filters("datasource")) { (acc, el) =>
        val JString(typ) = el \ "type"
        import org.json4s.native.JsonMethods._
        //val queryJson = compact(render(el \ "query" \ "query" ))
        //println(queryJson)
        acc.filter(typ, typeQuery(typ))
      }
      provide(ds)
    }
  }

  def conditionSetAggregation(agg: FiltersAggregationBuilder): Directive1[FiltersAggregationBuilder] = {
    userFilter.flatMap { filter =>
      `conditionSet+include`.flatMap { searchSq =>
        formatHits(searchSq).flatMap { conditionsMap =>
          parameters('conditionSet.?).flatMap {
            case Some(ids) if !ids.isEmpty =>
              val individual = conditionsMap.filterKeys(ids.contains).values.foldLeft(AggregationBuilders.filters("individual")) { (acc, c) =>
                val dd: JObject = "indices" -> ("query" -> ("bool" -> ("must" -> Set(parse(c.query)))))
                val withUserFilterQuery = filter merge dd
                val JArray(xs) = withUserFilterQuery \ "indices" \ "indices"
                val indices = xs.collect { case JString(s) => s}
                val q = indicesQuery(wrapperQuery(compact(render(withUserFilterQuery \ "indices" \ "query"))), indices: _*).noMatchQuery("none")
                println(s"conditionSetAggregation ${c.title}")
                println(s"${c.query}")
                //println(s"${pretty(render(withUserFilterQuery))}")
                acc.filter(c.title, wrapperQuery(c.query))
              }
              provide(agg.subAggregation(individual))
            case _ => provide(agg)
          }
        }
      }
    }
  }

  def includeAggregation(agg: FiltersAggregationBuilder) = {
    `conditionSet+include`.flatMap { searchSq =>
      formatHits(searchSq).flatMap { map =>
        parameters('conditionSet.?).flatMap { param0 =>
          val conditionSetQuery = map.filterKeys(param0.getOrElse("").contains).values.foldLeft(boolQuery()){ (bool, el) =>  bool.must(wrapperQuery(el.query))}
          parameters('include.?).flatMap {
          case None => provide(agg)
          case Some(param1) =>
            provide(agg.subAggregation(map.filterKeys(param1.contains).foldLeft(AggregationBuilders.filters("cross")){ case (acc, (_,el)) =>
              acc.filter(el.title, boolQuery().filter(conditionSetQuery).must(wrapperQuery(el.query)))
            }))
          }
        }
      }
    }
  }

  def datasourceBuckets(aggf: FiltersAggregationBuilder): Directive1[List[Filters.Bucket]] = {
    userFilter.flatMap { filter =>
      onSuccess(client.prepareSearch().setQuery(wrapperQuery(compact(render(filter)))).addAggregation(aggf).execute().future).flatMap { res =>
        res.getAggregations.asMap().toMap.get("datasource") match {
          case Some(f: Filters) => provide(f.getBuckets.toList)
          case unknown => reject()
        }
      }
    }
  }

  def getBuckets(bucket: Filters.Bucket, name: String):List[Filters.Bucket] = {
    bucket.getAggregations.asMap().toMap.get(name) match {
      case Some(f:Filters) => f.getBuckets.toList
      case _ => Nil
    }
  }

}
