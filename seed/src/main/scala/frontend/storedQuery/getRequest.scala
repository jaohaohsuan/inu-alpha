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
import protocol.storedQuery.Exchange.{SpanNearClause, MatchClause, NamedClause}
import read.storedQuery.StoredQueryIndex
import spray.http.StatusCodes._
import spray.routing.RequestContext

import scala.collection.JavaConversions._

object GetStoredQueryDetailRequest {
  def props(occur: String)(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[GetStoredQueryDetailRequest], ctx, storedQueryId, occur)
}

case class GetStoredQueryDetailRequest(ctx: RequestContext, storedQueryId: String, occur: String) extends PerRequest {

  import StoredQueryIndex._
  import context.dispatcher

  lazy val getItemDetail =
    prepareGet(storedQueryId)
      .setFetchSource(Array(s"collection.$occur"), null)
      .setTransformSource(true)
      .request()

  StoredQueryIndex.get(getItemDetail).map {
    case r: GetResponse => r.getSourceAsString
  }.recover { case _ => """{ "error": { } }""" } pipeTo self

  def processResult: Receive = {
    case json: String =>
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          complete(OK, compact(render(JObject("collection" -> (parse("""{ "version": "1.0" }""") merge (parse(json) \\ "must"))))))
        }
      }
  }
}


object GetStoredQueryRequest {
  def props(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[GetStoredQueryRequest], ctx, storedQueryId)
}

case class GetStoredQueryRequest(ctx: RequestContext, storedQueryId: String) extends PerRequest {

  import StoredQueryIndex._
  import context.dispatcher

  lazy val getItem =
    prepareGet(storedQueryId)
      .setFetchSource(Array("collection"), Array("collection.should", "collection.must", "collection.must_not"))
      .setTransformSource(true)
      .request()

  StoredQueryIndex.get(getItem).map {
    case r: GetResponse => r.getSourceAsString
  }.recover { case _ => """{ "error": { } }""" } pipeTo self

  def processResult: Receive = {
    case json: String =>
      /*val content = parse(json)
      log.debug(pretty(render(content)))*/
      response {
        URI( href =>  {
          respondWithMediaType(`application/vnd.collection+json`) {
            complete(OK, compact(render(parse(json) merge JObject(JField("collection", JObject(JField("href", JString(s"$href"))))))))
          }
        })
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
  ).flatten.foldLeft(QueryBuilders.boolQuery().mustNot(temporaryIdsQuery))(_ must _)

  search(prepareSearch
    .setQuery(queryDefinition)
    .setFetchSource(Array("collection.items"), null)
    .request).recover { case ex => """{ "error": { } }""" } pipeTo self


  def processResult: Receive = {
    case r: SearchResponse =>

      import scala.language.implicitConversions

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

object GetClauseTemplateRequest {
  def props(implicit ctx: RequestContext) =
    Props(classOf[GetClauseTemplateRequest], ctx)
}

case class GetClauseTemplateRequest(ctx: RequestContext) extends PerRequest {

  def sample(clause: String): JValue = {
    import protocol.storedQuery.ImplicitJsonConversions._
    clause match {
      case "named" => NamedClause("1", "template", "must")
      case "match" => MatchClause("hello search", "dialogs", "AND", "must")
      case "near" => SpanNearClause("term", "dialogs", 10, inOrder = false, "must")
    }
  }

  def processResult: Receive = {
    case clause: String =>
      response {
        URI { href =>

          val ver = JField("version", JString("1.0"))
          val data = JField("data", sample(clause))

          val template = JField("template", JObject(data))
          val collection = JField("collection", JObject(ver, JField("href", JString(s"$href")), template))

          respondWithMediaType(`application/vnd.collection+json`) {
            complete(OK, compact(render(JObject(collection))))
          }
        }
      }
  }
}

