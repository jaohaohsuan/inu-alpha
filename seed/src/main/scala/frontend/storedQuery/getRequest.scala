package frontend.storedQuery.getRequest

import akka.actor.Props
import akka.pattern._
import es.indices.storedQuery
import es.indices.logs
import frontend.CollectionJsonSupport.`application/vnd.collection+json`
import frontend.{Pagination, PerRequest}
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.MatchQueryBuilder
import protocol.storedQuery.Terminology._
import org.json4s
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import protocol.storedQuery.Exchange.{MatchClause, NamedClause, SpanNearClause}
import spray.http.Uri.Path
import storedQuery._
import spray.http.StatusCodes._
import spray.routing.RequestContext
import elastic.ImplicitConversions._
import scala.language.implicitConversions
import text.ImplicitConversions._
import scalaz._, Scalaz._
import scala.language.reflectiveCalls

import scala.collection.JavaConversions._

object GetStoredQueryDetailRequest {
  def props(occur: String)(implicit ctx: RequestContext, client: org.elasticsearch.client.Client, storedQueryId: String) =
    Props(classOf[GetStoredQueryDetailRequest], ctx, client, storedQueryId, occur)
}

case class GetStoredQueryDetailRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client ,storedQueryId: String, occur: String) extends PerRequest {

  import storedQuery._
  import context.dispatcher

  lazy val getItemDetail =
    prepareGet(storedQueryId)
      .setFetchSource(Array(s"occurs.$occur"), null)
      .setTransformSource(true)

  getItemDetail.execute().asFuture.map {
    case r: GetResponse => r.getSourceAsString
  }.recover { case _ => """{ "error": { } }""" } pipeTo self

  def processResult: Receive = {
    case json: String =>
      response {
        requestUri { uri =>
          respondWithMediaType(`application/vnd.collection+json`) {

            val items = json match {
              case "{}" => "[]"
              case _ => compact(render(parse(json) \\ occur)) richFormat Map("uri" -> s"""\\/$occur$$""".r.replaceAllIn(s"$uri", ""))
            }

            complete(OK,
              s"""{
                 |  "collection" : {
                 |    "version": "1.0",
                 |    "href" : "${uri}",
                 |
                 |    "items" : $items
                 |  }
                 |}
               """.stripMargin)
          }
        }
      }
  }
}

object GetStoredQueryRequest {
  def props(implicit ctx: RequestContext, client: org.elasticsearch.client.Client,  storedQueryId: String) =
    Props(classOf[GetStoredQueryRequest], ctx, client: org.elasticsearch.client.Client,  storedQueryId)
}

case class GetStoredQueryRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client,  storedQueryId: String) extends PerRequest {

  import storedQuery._
  import context.dispatcher
  implicit val formats = org.json4s.DefaultFormats

  lazy val getItem =
    prepareGet(storedQueryId)
      .setFetchSource(Array("item"), null)
      .setTransformSource(true)


  getItem.execute().asFuture.map {
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

    val prompts = Map("title" -> "模型名稱", "tags" -> "模型組")

    val template = data.extractOpt[List[JObject]].map(_.map {
      case o@JObject(("name", JString(name)) :: ("value", _) :: Nil) if prompts.contains(name) =>
        o merge JObject(("prompt", JString(prompts(name))))
      case o: JObject =>
        o merge JObject(("prompt", JString("")))
    }).map{ d => compact(render(JArray(d)))}.getOrElse("[]")

    s"""{
       | "collection" : {
       |   "version" : "1.0",
       |   "href" : "",
       |
       |   "items" : [
       |     {
       |       "href" : "${href}",
       |       "data" : ${compact(render(data))},
       |
       |       "links" : [
       |        { "rel" : "preview", "name" : "preview", "href" : "$href/preview" },
       |        ${Occurrences.map(n => s"""{ "rel" : "section", "name" : "$n", "href" : "$href/$n" }""").mkString(",")},
       |        ${BoolQueryClauses.map(n => s"""{ "rel" : "edit", "href" : "$href/$n" }""").mkString(",")}
       |       ]
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
  def props(queryString: Option[String] = None, queryTags: Option[String] = None, size: Int, from: Int)(implicit ctx: RequestContext, client: org.elasticsearch.client.Client) =
    Props(classOf[QueryStoredQueryRequest], ctx, client, queryString, queryTags, size, from)
}

case class QueryStoredQueryRequest(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client , queryString: Option[String], queryTags: Option[String], size: Int, from: Int) extends PerRequest {

  import context.dispatcher
  import org.elasticsearch.index.query.QueryBuilders
  import storedQuery._

  lazy val queryDefinition = Seq(
    queryString.map { QueryBuilders.queryStringQuery(_).field("_all") },
      queryTags.map { QueryBuilders.matchQuery("tags", _).operator(MatchQueryBuilder.Operator.OR) }
  ).flatten.foldLeft(QueryBuilders.boolQuery().mustNot(temporaryIdsQuery))(_ must _)

  prepareSearch
    .setQuery(queryDefinition)
    .setFetchSource(Array("item"), null)
    .setSize(size).setFrom(from)
    .execute()
    .asFuture.recover { case ex => """{ "error": { } }""" } pipeTo self


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
       |   "queries" : [ {
       |      "href" : "$href",
       |      "rel" : "search",
       |      "data" : [
       |        { "name" : "q", "prompt" : "search title or any terms" },
       |        { "name" : "tags", "prompt" : "" }
       |      ]
       |    } ],
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
  def props(implicit ctx: RequestContext, client: org.elasticsearch.client.Client , storedQueryId: String) =
    Props(classOf[Preview], ctx, client, storedQueryId)
}

case class Preview(ctx: RequestContext, implicit val client: org.elasticsearch.client.Client, storedQueryId: String) extends PerRequest {

  import context.dispatcher

  lazy val getQuery =
    prepareGet(storedQueryId)
      .setFetchSource(Array("query"), null)
      .setTransformSource(true)
      .execute().asFuture
      .map{ r => compact(render(parse(r.getSourceAsString) \ "query")) }

   def search(query: String) =
     logs.prepareSearch()
       .setQuery(query)
       .addField("vtt")
       .setHighlighterRequireFieldMatch(true)
       .setHighlighterPreTags("<em>")
       .setHighlighterPostTags("</em>")
       .addHighlightedField("agent*", 100, 0)
       .addHighlightedField("customer*", 100, 0)
       .addHighlightedField("dialogs", 100, 0)
       .execute().asFuture

  (for {
    query <- getQuery
    hits <- search(query)
  } yield hits) pipeTo self

  def processResult: Receive = {
    case r: SearchResponse =>
      response {
        requestUri { uri =>
          val `HH:mm:ss.SSS` = org.joda.time.format.DateTimeFormat.forPattern("HH:mm:ss.SSS")
          def startTime(value: logs.VttHighlightFragment): Int =
            `HH:mm:ss.SSS`.parseDateTime(value.start).getMillisOfDay

          val items = r.getHits.map { case logs.SearchHitHighlightFields(location, fragments) =>
            s"""{
               |  "href" : "${uri.withPath(Path(s"/$location")).withQuery(("_id", storedQueryId))}",
               |  "data" : [
               |    { "name" : "highlight", "array" : [ ${fragments.toList.sortBy { e => startTime(e) }.map { case logs.VttHighlightFragment(start, keywords) => s""""$start $keywords"""" }.mkString(",")} ] },
               |    { "name" : "keywords" , "value" : "${fragments.flatMap { _.keywords.split("""\s""") }.toSet.mkString(" ")}" }
               |  ]
               |}""".stripMargin
          }.mkString(",")

          complete(OK,
            s"""{
              |   "collection" : {
              |     "version" : "1.0",
              |     "href" : "$uri",
              |     "links" : [ ],
              |
              |     "items" : [ $items ]
              |   }
              |}""".stripMargin)
        }
      }
    case ex: Exception =>
      response {
        requestUri { uri =>
          log.error(ex, s"$uri")
          complete(InternalServerError,
            s"""{
               |  "collection" : {
               |    "version" : "1.0",
               |    "href" : "$uri",
               |    "error": { "message" : "${ex.getMessage}" }
               |  }
               |}""".stripMargin)
        }
      }
  }
}
