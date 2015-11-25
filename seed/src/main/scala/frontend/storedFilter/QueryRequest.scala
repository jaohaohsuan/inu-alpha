package frontend.storedFilter

import akka.actor.Props
import frontend.CollectionJsonSupport._
import frontend.{CollectionJsonSupport, PerRequest}
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

  def unapply(res: org.elasticsearch.action.search.SearchResponse): Option[List[JValue]] = {
    Some(res.getHits.map { h =>
      ("id" -> h.id()) ~ ("data" -> parse(h.sourceAsString()))
    }.toList)
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
        .setSize(size).setFrom(from)
        .execute()
        .asFuture

      search pipeTo self
    }
  }(ctx)

  def processResult = {
    case SearchResponseHits(hits) =>
      response {
        requestUri { uri =>
          collection { json =>
            respondWithMediaType(`application/vnd.collection+json`) {
              val itemUri = uri.withQuery()
              complete(OK, json.mapField {
                case ("template", _) => "template" -> NewFilter("untitled").asTemplate
                case ("queries", _) => "queries" -> List(
                  ("href" -> s"${uri.withQuery()}") ~~
                    ("rel" -> "search") ~~
                    ("data" -> List(("name" -> "q") ~~ ("prompt" -> "search title or field")))
                )
                case ("items", _) => "items" -> hits.map { item => item.mapField {
                  case ("id", JString(id)) => "href" -> s"${itemUri.withPath(itemUri.path / id).withQuery()}"
                  case ("data", JObject(xs)) => "data" -> xs.map { case (f: String, v: JValue) => ("name" -> f) ~~ ("value" -> v) }
                  case x => x
                }
                }
                case ("links", _) => "links" -> List(("rel" -> "edit") ~~ ("href" -> s"${itemUri.withPath(itemUri.path / "temporary")}"))
                case x => x
              })
            }
          }
        }
      }
  }

}

