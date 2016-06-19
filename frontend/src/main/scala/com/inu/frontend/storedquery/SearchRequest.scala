package com.inu.frontend.storedquery

import akka.actor.Props
import akka.pattern._
import akka.util.Timeout
import com.inu.frontend.CollectionJsonSupport._
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.{BoolQueryBuilder, MatchQueryBuilder, QueryBuilder, QueryBuilders}
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import spray.http.StatusCodes._
import spray.routing.RequestContext

import scala.concurrent.Future
import scala.concurrent.duration._

object SearchRequest {

  def buildQueryDefinition(queryString: Option[String] = None, queryTags: Option[String] = None): BoolQueryBuilder = {
    import org.elasticsearch.index.query.QueryBuilders._
    Seq(
      queryString.map { queryStringQuery(_).field("_all") },
      queryTags.map { matchQuery("tags", _).operator(MatchQueryBuilder.Operator.OR) }
    ).flatten.foldLeft(boolQuery().mustNot(QueryBuilders.idsQuery(".percolator").ids("temporary")))(_ must _)
  }

  def props(queryString: Option[String] = None, queryTags: Option[String] = None, size: Int = 10, from: Int = 0)(implicit ctx: RequestContext, client: org.elasticsearch.client.Client) =
    Props(classOf[SearchRequest], ctx, client, buildQueryDefinition(queryString, queryTags), size, from)
}

case class SearchRequest(ctx: RequestContext, implicit val client: Client,
                         qb: QueryBuilder,
                         size: Int, from: Int) extends PerRequest with CollectionJsonSupport {

  import com.inu.frontend.elasticsearch.ImplicitConversions._
  import com.inu.frontend.UriImplicitConversions._
  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  (for {
    tags <- Future { "for demo only" }
    searchResponse <- client.prepareSearch("stored-query").setTypes(".percolator")
      .setQuery(qb)
      .setFetchSource(Array("item"), null)
      .setSize(size).setFrom(from)
      .execute().future
  } yield (searchResponse, tags)) pipeTo self

  def processResult: Receive = {
    case (r: SearchResponse ,tags: String) =>
      response {
        requestUri(implicit uri => {
          pagination(r)(uri) { p =>
              respondWithMediaType(`application/vnd.collection+json`) {
              val items = JField("items", parse(s"$r") \ "hits" \ "hits" \ "_source" \ "item" transformField {
                case JField("href", JString(s)) => ("href", JString(s"${uri.withPath(uri.path./(s)).withQuery()}"))
              })

              val temporary = ("rel" -> "edit") ~~ ("href" -> s"${uri.withQuery() / "temporary"}")
              val links = JField("links", JArray(temporary :: p.links))

              val template = JField("template", "data" -> Set(
                ("name" -> "title") ~~ ("value" -> "query0"),
                ("name" -> "tags") ~~ ("value" -> "tag0 tag1")
              ))

              val queries = JField("queries",
                ("href" -> s"${uri.withQuery()}") ~~
                  ("rel" -> "search") ~~
                  ("data" -> Set(
                    ("name" -> "q") ~~ ("prompt" -> "search title or any terms"),
                    ("name" -> "tags") ~~ ("prompt" -> ""),
                    ("name" -> "size") ~~ ("prompt" -> "size of displayed items"),
                    ("name" -> "from") ~~ ("prompt" -> "items display from")
                  ))
              )
              complete(OK, links :: items :: queries :: template :: Nil)
            }
          }
        })
      }
  }
}