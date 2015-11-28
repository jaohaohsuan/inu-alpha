package frontend.storedFilter

import akka.actor.Props
import es.indices.storedFilter._
import frontend.CollectionJsonSupport._
import frontend.{CollectionJsonSupport, PerRequest}
import org.elasticsearch.client.Client
import org.json4s.JsonAST._
import org.json4s.{DefaultFormats, Formats}
import spray.routing.RequestContext
import elastic.ImplicitConversions._
import org.json4s.native.JsonMethods._
import spray.http.StatusCodes._
import akka.pattern._
import org.json4s.JsonDSL._


object GetItemClausesRequest {
  def props(typ: String, id: String, occur: String)(implicit ctx: RequestContext, client: Client) =
    Props(classOf[GetItemClausesRequest],ctx, client, typ, id, occur)
}

case class GetItemClausesRequest(ctx: RequestContext, private implicit val client: Client, typ: String, id: String, occur: String) extends PerRequest with CollectionJsonSupport {

  import context.dispatcher
  implicit def json4sFormats: Formats = DefaultFormats

  import es.indices.storedFilter._

  (for {
    res <- prepareGet(typ, id)
      .setFetchSource(occur, null)
      .execute().future
    if res.isExists
  } yield parse(res.getSourceAsString)) pipeTo self


  def processResult = {
    case source: JObject =>
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          requestUri { uri =>
            collection { json =>
              complete(OK, json.transformField {
                case ("items", _) =>
                  val items = source \ occur match {
                    case JArray(xs) => xs.map { o => o.mapField {
                      case ("href", JString(path)) =>
                        val href = path.split("/").foldLeft(uri.withPath(uri.path.reverse.tail.tail.reverse)){ (acc, e) => acc.withPath( acc.path / e )}
                        "href" -> JString(s"$href")
                      case ("data", JObject(ys)) => "data" -> JArray(ys.collect { case (f: String, v: JValue) => ("name" -> f) ~~ ("value" -> v) })
                      case x => x
                    }}
                    case _ => Nil
                  }
                  "items" -> JArray(items)
              })
            }
          }
        }
      }
  }

}
