package frontend.storedQuery.getRequest

import akka.actor.Props
import akka.pattern._
import frontend.CollectionJsonSupport.`application/vnd.collection+json`
import frontend.{Pagination, PerRequest}
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.MatchQueryBuilder
import org.json4s
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import protocol.storedQuery.Exchange.{MatchClause, NamedClause, SpanNearClause}
import read.storedQuery.StoredQueryIndex
import read.storedQuery.StoredQueryIndex._
import spray.http.StatusCodes._
import spray.routing.RequestContext
import scalaz._, Scalaz._

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
  implicit val formats = org.json4s.DefaultFormats

  lazy val getItem =
    prepareGet(storedQueryId)
      .setFetchSource(Array("item"), null)
      .setTransformSource(true)
      .request()

  StoredQueryIndex.get(getItem).map {
    case r: GetResponse => r.getSourceAsString
  }.recover { case _ => """{ "error": { } }""" } pipeTo self

  def processResult: Receive = {
    case json: String =>
      response {
        URI( href =>  {
          respondWithMediaType(`application/vnd.collection+json`) {
            complete(OK, itemRepresentation(parse(json), href))
          }
        })
      }
  }

  def itemRepresentation(json: JValue, href: java.net.URI)(implicit formats: Formats): String = {

    val data = json \ "item" \ "data"

    val template = data.extractOpt[List[JObject]].map(_.map {
      case o@JObject(("name", JString(name)) :: ("value", _) :: Nil) =>
        o merge JObject(("prompt", JString("")))
      case o: JObject =>
        o merge JObject(("prompt", JString("")))
    }).map{ d => compact(render(JArray(d)))}.getOrElse("[]")

    s"""{
       | "collection" : {
       |   "version" : "1.0",
       |   "href" : "",
       |
       |   "links" : [
       |
       |   ],
       |
       |   "items" : [
       |     {
       |       "href" : "${href}",
       |       "data" : ${compact(render(data))}
       |     }
       |   ],
       |
       |   "template" : {
       |      "data" : ${template}
       |   }
       | }
       |}""".stripMargin
  }
}

object QueryStoredQueryRequest {
  def props(queryString: Option[String] = None, queryTags: Option[String] = None, size: Int, from: Int)(implicit ctx: RequestContext) =
    Props(classOf[QueryStoredQueryRequest], ctx, queryString, queryTags, size, from)
}

case class QueryStoredQueryRequest(ctx: RequestContext, queryString: Option[String], queryTags: Option[String], size: Int, from: Int) extends PerRequest {

  import context.dispatcher
  import org.elasticsearch.index.query.QueryBuilders
  import read.storedQuery.StoredQueryIndex._

  lazy val queryDefinition = Seq(
    queryString.map { QueryBuilders.queryStringQuery(_).field("_all") },
      queryTags.map { QueryBuilders.matchQuery("tags", _).operator(MatchQueryBuilder.Operator.OR) }
  ).flatten.foldLeft(QueryBuilders.boolQuery().mustNot(temporaryIdsQuery))(_ must _)

  search(prepareSearch
    .setQuery(queryDefinition)
    .setFetchSource(Array("item"), null)
    .setSize(size).setFrom(from)
    .request).recover { case ex => """{ "error": { } }""" } pipeTo self


  def processResult: Receive = {
    case r: SearchResponse =>

      implicit val formats = org.json4s.DefaultFormats

      val hits: List[json4s.JValue] = r.getHits.map { h => parse(h.sourceAsString()) \ "item" }.toList

      response {
        requestUri { implicit uri =>
          respondWithMediaType(`application/vnd.collection+json`) {
            complete(OK, collectionRepresentation(hits, Pagination(size, from, r.getHits.totalHits).links ,uri))
          }
        }
      }
  }

  def collectionRepresentation(hits: List[json4s.JValue], pagination: Iterable[String] ,uri: spray.http.Uri)(implicit formats: Formats) = {

    val href = uri.withQuery(Map.empty[String,String])

   val items = hits.map {
      case o: JObject =>
        val `item-href` = (o \ "id").extractOpt[String].map { id => s"$href/$id"}.getOrElse("")
        val data = compact(render(o \ "data"))
        s"""{
           |  "href" : "${`item-href`}",
           |  "data" : $data
           |}""".stripMargin
      case _ => ""

    }.filter(_.nonEmpty).mkString(",")

    s"""{
       | "collection" : {
       |   "version" : "1.0",
       |   "href" : "$href",
       |
       |   "links" : [
       |      ${pagination.mkString(",")}
       |   ],
       |
       |   "items" : [$items],
       |
       |   "template" : {
       |      "data" : [
       |        {"name":"title","value":""},
       |        {"name":"tags","value":""}
       |      ]
       |   }
       | }
       |}""".stripMargin
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

object Preview {
  def props(implicit ctx: RequestContext, storedQueryId: String) =
    Props(classOf[Preview], ctx, storedQueryId)
}

case class Preview(ctx: RequestContext, storedQueryId: String) extends PerRequest {

  import context.dispatcher

  lazy val getQuery =
    prepareGet(storedQueryId)
      .setFetchSource(Array("query"), null)
      .setTransformSource(true)
      .request()

  StoredQueryIndex.get(getQuery).map {
    case r: GetResponse =>
      val boolQuery = compact(render(parse(r.getSourceAsString) \ "query"))
      boolQuery
  } recover { case _ => """{ "error": { } }"""  } pipeTo self


  def processResult: Receive = {
    case json: String =>

      response {
        URI( href =>  {
          respondWithMediaType(`application/vnd.collection+json`) {
            complete(OK, json)
          }
        })
      }

  }
}
