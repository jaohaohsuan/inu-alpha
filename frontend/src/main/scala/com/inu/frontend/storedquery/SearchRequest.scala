package com.inu.frontend.storedquery

import akka.actor.Props
import akka.pattern._
import akka.util.Timeout
import com.inu.frontend.CollectionJsonSupport._
import com.inu.frontend.directive.StoredQueryDirectives
import com.inu.frontend.{CollectionJsonSupport, PerRequest}
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.{BoolQueryBuilder, MatchQueryBuilder, QueryBuilder, QueryBuilders}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.{JObject => _, JValue => _, _}
import spray.http.StatusCodes._
import spray.http.Uri
import spray.routing._

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
                         size: Int, from: Int) extends PerRequest with CollectionJsonSupport with StoredQueryDirectives {

  import com.inu.frontend.elasticsearch.ImplicitConversions._
  import com.inu.frontend.UriImplicitConversions._
  implicit val executionContext = context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  (for {
   // tags <- Future { "for demo only" }
    searchResponse <- client.prepareSearch("stored-query").setTypes(".percolator")
      .setQuery(qb)
      .setFetchSource(Array("item"), null)
      .setSize(size).setFrom(from)
      .execute().future
  } yield searchResponse) pipeTo self

  def extractItems(sr: SearchResponse): Directive1[List[JValue]] = {
    requestUri.flatMap { uri =>
      import com.inu.frontend.UriImplicitConversions._
      val dropQuery = uri.drop("q", "size", "from", "tags")

      val hits = parse(s"$sr") \ "hits" \ "hits" match {
        case o: JObject => o :: Nil
        case JArray(xs) => xs
        case _ => Nil
      }
      val items = hits.map { h =>
        val JString(id) = h \ "_id"
        val item = h \ "_source" \ "item"
        item.merge(("href" -> s"${dropQuery.withPath(uri.path./(id))}"): JObject)
      }
      provide(items)
    }
  }

  def processResult: Receive = {
    case r: SearchResponse =>
      response {
        requestUri(implicit uri => {
          pagination(r)(uri) { p =>
            extractItems(r) { items =>
              respondWithMediaType(`application/vnd.collection+json`) {
                tags { allTags =>
                  val href = JField("href", JString(s"${uri.withQuery()}"))
                  val temporary = ("rel" -> "edit") ~~ ("href" -> s"${uri.withQuery() / "temporary"}")
                  val links = JField("links", JArray(temporary :: p.links))

                  val template = JField("template", "data" -> Set(
                    ("name" -> "title") ~~ ("value" -> "query0") ~~ ("prompt" -> "title can not be empty"),
                    ("name" -> "tags") ~~ ("value" -> allTags) ~~ ("prompt" -> "optional")
                  ))

                  val queries = JField("queries", JArray(("href" -> s"${uri.withQuery()}") ~~
                    ("rel" -> "search") ~~
                    ("data" -> Set(
                      ("name" -> "q") ~~ ("prompt" -> "search title or any terms"),
                      ("name" -> "tags") ~~ ("prompt" -> allTags),
                      ("name" -> "size") ~~ ("prompt" -> "size of displayed items"),
                      ("name" -> "from") ~~ ("prompt" -> "items display from")
                    )) :: Nil)
                  )
                  complete(OK, href :: links :: JField("items", JArray(items)) :: queries :: template :: Nil)

              }
              }
            }
          }
        })
      }
  }
}