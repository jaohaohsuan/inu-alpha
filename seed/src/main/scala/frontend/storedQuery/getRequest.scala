package frontend.storedQuery.getRequest

import akka.actor.Props
import akka.pattern._
import frontend.CollectionJsonSupport.`application/vnd.collection+json`
import frontend.PerRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.search.SearchHit
import org.json4s._
import org.json4s.native.JsonMethods._
import read.storedQuery.StoredQueryIndex
import spray.http.StatusCodes._
import spray.routing.RequestContext

import scala.collection.JavaConversions._

object GetStoredQueryRequest {
  def props(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[GetStoredQueryRequest], ctx, storedQueryId)
}

case class GetStoredQueryRequest(ctx: RequestContext, storedQueryId: String) extends PerRequest {

  import StoredQueryIndex._
  import context.dispatcher

  val getItem =
    prepareGet(storedQueryId).setFetchSource(Array("collection"), Array("collection.should", "collection.must", "collection.must_not")).setTransformSource(true)
    .request()

  StoredQueryIndex.get(getItem).map {
    case r: GetResponse => r.getSourceAsString
  }.recover { case _ => """{ "error": { } }""" } pipeTo self

  def processResult: Receive = {
    case json: String =>
      //compact(render("collection" -> (parse(json) merge JObject(("version" -> JString("1.0"))))))
      val content = parse(json)
      log.debug(pretty(render(content)))
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          complete(OK, json)
        }
      }
  }
}

object QueryStoredQueryRequest {
  def props(queryString: Option[String] = None, queryTags: Option[String] = None)(implicit ctx: RequestContext) =
    Props(classOf[QueryStoredQueryRequest], ctx, queryString, queryTags)
}

case class QueryStoredQueryRequest(ctx: RequestContext, queryString: Option[String], queryTags: Option[String]) extends PerRequest {

  import context.dispatcher
  import org.elasticsearch.index.query.QueryBuilders
  import read.storedQuery.StoredQueryIndex._

  lazy val queryDefinition = Seq(
    queryString.map { QueryBuilders.queryStringQuery(_).field("_all") },
      queryTags.map { QueryBuilders.matchQuery("tags", _).operator(MatchQueryBuilder.Operator.OR) }
  ).flatten.foldLeft(QueryBuilders.boolQuery())(_ must _)

  search(prepareSearch
    .setQuery(queryDefinition)
    .setFetchSource(Array("collection.items"), null)
    .request).recover { case ex => """{ "error": { } }""" } pipeTo self


  def processResult: Receive = {
    case r: SearchResponse =>

      implicit def hitConvert(h: SearchHit): JValue = (parse(h.sourceAsString()) \\ "items")(0)
      implicit def javaValueList(items: List[JValue]): String = compact(render(JArray(items)))

      val json =  s"""{
        | "collection": {
        |   "version": "1.0",
        |   "items": [ ${r.getHits.foldLeft(List.empty[JValue])(_.::(_)): String}]
        | }
        |}""".stripMargin

      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          complete(OK, json: String)
        }
      }
  }
}

