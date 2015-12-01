package frontend.storedFilter

import akka.actor.Props
import frontend.CollectionJsonSupport._
import frontend.{Pagination, CollectionJsonSupport, PerRequest}
import org.elasticsearch.action.search.SearchResponse
import org.json4s.JsonAST.{JString, JValue, JObject}
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import spray.routing.RequestContext
import spray.http.StatusCodes._
import org.json4s.JsonDSL._
import org.elasticsearch.client.Client
import akka.pattern._
import elastic.ImplicitConversions._
import scala.collection.JavaConversions._


import scala.concurrent.Future

object QueryRequest {
  def props(implicit ctx: RequestContext, client: Client) = Props(classOf[QueryRequest], ctx, client)
}

object SearchResponseHits {

  def unapply(res: org.elasticsearch.action.search.SearchResponse): Option[(List[JValue], Long)] = {
    Some(res.getHits.map { h =>
      ("id" -> h.id()) ~ ("data" -> parse(h.sourceAsString()))
    }.toList, res.getHits.totalHits())
  }
}

case class QueryRequest(ctx: RequestContext, private implicit val client: Client) extends PerRequest with CollectionJsonSupport {

  import context.dispatcher

  implicit def json4sFormats: Formats =  DefaultFormats

  requestUri { uri =>
    parameter('q.?, 'size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (q, size, from) =>  _ =>
      import es.indices.storedFilter._
      val search: Future[SearchResponse] = prepareSearch(s"${uri.path.reverse.head}")
        .setQuery(buildQueryDefinition(q))
        .setFetchSource("title", null)
        .setSize(size).setFrom(from)
        .execute()
        .future

      search pipeTo self
    }
  }(ctx)

  def processResult = {
    case SearchResponseHits((hits, totals)) =>
      response {
        requestUri { implicit uri =>
          collection { json =>
            respondWithMediaType(`application/vnd.collection+json`) {
              parameter('size.as[Int] ? 10, 'from.as[Int] ? 0 ) { (size, from) =>
                val paginationLinks = Pagination(size, from, totals).links.map { l => parse(l)}.toList
                val itemUri = uri.withQuery()
                complete(OK, json.transformField {
                  case (k@"href", _) => k -> s"${uri.withQuery()}"
                  case (k@"template", _) => k -> NewFilter("untitled").asTemplate
                  case q@("queries", _) => q.copy(_2 =
                    ("href" -> s"${uri.withQuery()}") ~~
                      ("rel" -> "search") ~~
                      ("data" -> ("name" -> "q") ~~ ("prompt" -> "search title or field") :: Nil) :: Nil)
                  case (k@"items", _) => k -> hits.map {
                    _.transformField {
                      case ("id", JString(id)) => "href" -> s"${itemUri.withPath(itemUri.path / id).withQuery()}"
                      case ("data", JObject(xs)) => "data" -> xs.map { case (f: String, v: JValue) => ("name" -> f) ~~ ("value" -> v) }
                    }
                  }
                  case ("links", _) =>
                    "links" ->  List(("rel" -> "edit") ~~ ("href" -> s"${itemUri.withPath(itemUri.path / "temporary")}")).++(paginationLinks)
                })
              }
            }
          }
        }
      }
  }

}

