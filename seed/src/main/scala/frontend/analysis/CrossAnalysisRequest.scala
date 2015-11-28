package frontend.analysis

import akka.actor.Props
import akka.pattern._
import elastic.ImplicitConversions._
import es.indices.storedQuery._
import es.indices.{logs, storedQuery}
import frontend.PerRequest
import frontend.storedQuery.getRequest.{CollectionJsonBuilder, QueryStoredQueryRequest}
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.index.query.{BoolQueryBuilder, QueryBuilder, QueryBuilders, WrapperQueryBuilder}
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.filters.Filters.Bucket
import org.elasticsearch.search.aggregations.bucket.filters.{Filters, FiltersAggregationBuilder}
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import spray.http.StatusCodes._
import spray.http.Uri
import spray.routing.RequestContext
import frontend.UriImplicitConversions._
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Try

object Condition {

  def apply(h: SearchHit)(implicit json4sFormats: Formats): Condition = {
    val source = parse(h.sourceAsString())
    val title = (source \ "title").extract[String]
    val query = compact(render(source \ "query"))
    Condition(h.id, title, query, "", Seq.empty)
  }

  def set(conditions: Seq[String]) = Condition("set", "set", "", "set", conditions)
}

case class Condition(storedQueryId: String, title: String, query: String, state: String = "", conditions: Iterable[String], hits: Long = 0) {

  def count(implicit queries: Map[String, Condition], filter: QueryBuilder, client: Client) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val qb = conditions.flatMap(queries.get).foldLeft(boolQuery()){ (acc, c) => acc.must(wrapperQuery(c.query)) }
    client.prepareCount("logs*")
      .setQuery(qb.filter(filter))
      .execute().future.map { resp =>
      //set hits
      queries.getOrElse(storedQueryId, this).copy(hits = resp.getCount, state = state)
    }
  }
}

class ConditionSet(conditions: Seq[String]) {

  def exclude(storedQueryId: String): Condition =
    Condition(storedQueryId, storedQueryId, "", "excludable", conditions.filterNot(_ == storedQueryId))

  def include(storedQueryId: String): Condition =
    Condition(storedQueryId, storedQueryId, "", "includable", conditions.+:(storedQueryId))

  def all = Condition("set", "set", "", "set", conditions)

}

class FurtherLinks(uri: Uri, storedQueryId: String) {

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

object CrossAnalysisRequest {
  def props(conditionSet: Seq[String], include: Seq[String], exclude: Seq[String])(implicit ctx: RequestContext, client: org.elasticsearch.client.Client): Props =
    Props(classOf[CrossAnalysisRequest], ctx, client, conditionSet, include, exclude)
}

object CrossAnalysisSourceRequest {

  import common.ImplicitPrint._
  import es.indices.storedQuery._
  import org.elasticsearch.index.query.QueryBuilders._

  def props(exclude: Seq[String], bodyBuilder: CollectionJsonBuilder)(queryString: Option[String] = None,
                                   queryTags: Option[String] = None,
                                   size: Int = 10, from: Int = 0)
                                   (implicit ctx: RequestContext, client: org.elasticsearch.client.Client) = {
    s"CrossAnalysisSourceRequest.props $exclude".println()
    Props(classOf[QueryStoredQueryRequest],
      ctx, client,
      bodyBuilder,
      buildQueryDefinition(queryString, queryTags).mustNot(idsQuery().ids(exclude: _*)),
      size, from)
  }

}

case class CrossAnalysisRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client,
                                conditionSet: Seq[String], include: Seq[String], exclude: Seq[String])
  extends PerRequest {

  import akka.pattern._
  import context.dispatcher
  import storedQuery._

  import scala.collection.JavaConversions._
  implicit def json4sFormats: Formats = DefaultFormats
  implicit def seqToSet(value: Seq[String]): ConditionSet = new ConditionSet(value)

  s"conditionSet=$conditionSet, include=$include, exclude=$exclude".logDebug()

  lazy val fetchStoredQueries =
    prepareSearch
      .setQuery(idsQuery(".percolator").addIds(conditionSet ++ include ++ exclude))
      .setFetchSource(Array("query", "title"), null)
      .execute.future
      .map { resp =>
        resp.getHits.foldLeft(Map.empty[String, Condition]){ (acc, h) => acc + (h.id() -> Condition(h))}
      }

  lazy val process = parameters('type.?) { typ => _ =>
    fetchStoredQueries.flatMap { implicit queries =>
      import es.indices.logs._
      implicit val filter: BoolQueryBuilder = typ.asTypeQuery
      for {
        excluded <- Future.traverse(exclude) { c => conditionSet.exclude(c).count }
        included <- Future.traverse(include) { c => conditionSet.include(c).count }
        set <- conditionSet.all.count
      } yield (excluded ++ included).:+(set)
    } pipeTo self
  }

  process(ctx)

  def processResult: Receive = {
    case xs: Seq[_] =>
      response {
        requestUri { uri =>
          parameters('type.?) { typ =>

            val items = xs.map { case Condition(storedQueryId, title, _, state, _, hits) =>

              implicit def toLink(id: String): FurtherLinks = new FurtherLinks(uri,id)

              val logsLink = """{ "rel" : "logs", "render" : "grid", "name": "%s", "href" : "%s"}"""

              val logs = title match {
                case "set" => typ.map { _.split("""(\s+|,)""").map { t => logsLink.format(t, uri.withPath(uri.path / "logs").withExistQuery(("type", t))) }.toList }
                  .getOrElse(List(logsLink.format("*", uri.withPath(uri.path / "logs"))))
                case _ => List.empty
              }

              log.debug(s"$uri extracted type is ${typ.getOrElse("")}")

              val links = (storedQueryId.action0(state) :: storedQueryId.action1(state) :: logs).filter(_.nonEmpty).mkString(",")
              s"""{
                   | "data" : [ { "name" : "hits", "value" : $hits }, { "name" : "title", "value" : "$title" }, { "name" : "state", "value" : "$state"} ],
                   | "links" : [ $links ]
                   |}""".stripMargin
              }.mkString(",")

            complete(OK, s"""{
                           |  "collection" : {
                           |    "version" : "1.0",
                           |    "href" : "$uri",
                           |    "links" : [
                           |     { "rel" : "source", "href" : "${uri.withPath(uri.path / "source")}" },
                           |     { "rel" : "graph", "render" : "bar", "href" : "${uri.withPath(uri.path / "graph0")}" },
                           |     { "rel" : "graph", "render" : "line", "href" : "${uri.withPath(uri.path / "graph1")}" }
                           |    ],
                           |
                           |    "items" : [ $items ]
                           |  }
                           |}""".stripMargin)
          }
        }
      }
    case ex: Exception =>
      response {
        requestUri { uri =>
          log.error(ex, s"$uri")
          complete(InternalServerError,
            s"""{
               |  "error" : {
               |    "message" : "${ex.getMessage}"
               |  }
               |}""".stripMargin)
        }
      }
  }
}

case class StoredQueryQuery(title: String, query: String)

object StoredQueryQuery {
  def unapply(searchHit: SearchHit): StoredQueryQuery = {
    implicit def json4sFormats: Formats = DefaultFormats
    val source = parse(searchHit.sourceAsString())
    val title = (source \ "title").extract[String]
    StoredQueryQuery(title, compact(render(source \ "query")))
  }
}


object ConditionSetBarChartRequest {
  def props(conditionSet: Seq[String])(implicit ctx: RequestContext, client: org.elasticsearch.client.Client) =
    Props(classOf[ConditionSetBarChartRequest], ctx, client, conditionSet)
}

case class ConditionSetBarChartRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client, conditionSet: Seq[String]) extends PerRequest {

  import akka.pattern._
  import context.dispatcher
  import storedQuery._
  implicit def json4sFormats: Formats = DefaultFormats

  def buildStoredQueryAgg(source: FiltersAggregationBuilder) =
    prepareSearchStoredQueryQuery(conditionSet).execute.future
      .map(_.getHits.foldLeft(List.empty[(String, WrapperQueryBuilder)]){ (acc, h) =>
        StoredQueryQuery.unapply(h) match {
                case StoredQueryQuery(title, q) if q.nonEmpty => (title, QueryBuilders.wrapperQuery(q)) :: acc
                case _ => acc
              }})
      .map {
        case Nil => source
        case xs => source.subAggregation(xs.foldLeft(AggregationBuilders.filters("individual")){ (acc, e) =>
          val (title, query) = e
          acc.filter(title, query)
        })
      }

  def search(agg: FiltersAggregationBuilder) = logs.prepareSearch().addAggregation(agg).execute().future

  (for {
    source <- logs.buildSourceAgg()
    individual <- buildStoredQueryAgg(source)
    result <- search(individual).map(_.getAggregations)
    agg = if(result == null) None else result.asMap().toMap.get("source")
  } yield agg) pipeTo self

  def processResult = {
    case Some(agg: Filters) =>
      response {
        val json = agg.getBuckets.foldLeft(List.empty[JObject]) { (acc0, b0: Bucket) =>

          val arr = Try({ b0.getAggregations.asMap().toMap.get("individual") }).map {
            case Some(filters: Filters) => filters.getBuckets
              .foldLeft(List.empty[JArray]){ (acc1, b1) => acc1 :+ JArray(List(JString(s"${b1.getKey}"), JInt(b1.getDocCount))) }
            case _ => List(JArray(List(JString("*"), JInt(b0.getDocCount.toInt))))
          }.getOrElse(List.empty)

          acc0 :+ JObject(List("key" -> JString(s"${b0.getKey}"), "values" -> JArray(arr)))
        }
        complete(OK, s"${pretty(render(json))}")
      }
  }
}

object CrossAnalysisLineChartRequest {
  def props(conditionSet: Seq[String], includable: Seq[String])(implicit ctx: RequestContext, client: org.elasticsearch.client.Client) =
    Props(classOf[CrossAnalysisLineChartRequest], ctx, client, conditionSet, includable)
}

case class CrossAnalysisLineChartRequest (ctx: RequestContext, implicit val client: org.elasticsearch.client.Client, conditionSet: Seq[String], includable: Seq[String]) extends PerRequest {

  import context.dispatcher

  implicit class Ids0(ids: Seq[String]) {
    import QueryBuilders._
    def buildQuery =
      prepareSearchStoredQueryQuery(ids).execute.future
        .map(_.getHits.foldLeft(QueryBuilders.boolQuery()){ (acc, h) =>
          StoredQueryQuery.unapply(h) match {
            case StoredQueryQuery(title, q) if q.nonEmpty => acc.must(QueryBuilders.wrapperQuery(q))
            case _ => acc
          }})

    def toAgg(source: FiltersAggregationBuilder, filter: QueryBuilder) =
      prepareSearchStoredQueryQuery(ids).execute.future
        .map(_.getHits.foldLeft(List.empty[(String, QueryBuilder)]){ (acc, h) =>
          StoredQueryQuery.unapply(h) match {
            case StoredQueryQuery(title, q) if q.nonEmpty => (title, boolQuery().filter(filter).must(wrapperQuery(q))) :: acc
            case _ => acc
          }})
        .map {
          case Nil => source
          case xs => source.subAggregation(xs.foldLeft(AggregationBuilders.filters("cross")){ (acc, e) =>
            val (title, query) = e
            acc.filter(title, query)
          })
        }
  }

  (for {
    source <- logs.buildSourceAgg()
    filter <- conditionSet.buildQuery
    cross <- includable.toAgg(source, filter)
    result <- logs.prepareSearch().addAggregation(cross).execute().future.map(_.getAggregations)
    agg = if(result == null) None else result.asMap().toMap.get("source")
  } yield agg) pipeTo self

  def processResult = {

    case Some(agg: Filters) =>
      response {
        val json = agg.getBuckets.foldLeft(List.empty[JObject]) { (acc0, b0: Bucket) =>
          val zero = JObject("label" -> JString("*"), "y" -> JInt(b0.getDocCount.toInt), "x" -> JInt(0)) :: Nil
          val arr = Try({ b0.getAggregations.asMap().toMap.get("cross") }).map {
           case Some(filters: Filters) =>
             filters.getBuckets.foldLeft(zero){ (acc1, b1) =>
               JObject("label" -> JString(s"${b1.getKey}"), "y" -> JInt(b1.getDocCount), "x" -> JInt(acc1.size)) :: acc1
             }
           case _ => zero
         }.getOrElse(zero).reverse

          acc0 :+ JObject(List("key" -> JString(s"${b0.getKey}"), "values" -> JArray(arr)))
        }
        complete(OK, s"${pretty(render(json))}")
      }
  }
}
