package frontend.storedFilter

import akka.actor.Props
import es.indices.logs
import frontend.CollectionJsonSupport._
import frontend.{CollectionJsonSupport, PerRequest}
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.client.Client
import org.elasticsearch.common.collect.ImmutableOpenMap
import org.elasticsearch.common.compress.CompressedXContent
import org.json4s
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import spray.routing._
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import akka.pattern._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import scala.collection.JavaConversions._

import scala.concurrent.Future

object GetItemRequest {
  def props(typ: String, id: String)(implicit ctx: RequestContext, client: Client) = Props(classOf[GetItemRequest],ctx, client, typ, id)
}

case class GetItemRequest(ctx: RequestContext, private implicit val client: Client, typ: String, id: String) extends PerRequest with CollectionJsonSupport {

  import context.dispatcher
  implicit def json4sFormats: Formats =  DefaultFormats

  def getTemplate =
    logs.getTemplate.asFuture.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get.mappings())

  def properties(typ: String): Directive1[JValue] = onSuccess(getTemplate.map { x => parse(s"${x.get(typ)}") \ typ \ "properties" })

  requestUri { uri => _ =>
    import es.indices.storedFilter._
      val getItem: Future[GetResponse] = prepareGet(typ, id)
        .execute()
        .asFuture
     getItem pipeTo self
  }(ctx)

  def processResult = {
    case r: GetResponse =>
      response {
        respondWithMediaType(`application/vnd.collection+json`) {
          requestUri { uri =>
            item(read[Map[String, Any]](r.getSourceAsString)) { json =>
              properties(typ) { properties =>
                val JObject(fields) = properties
                complete(OK, json.mapField {
                  case ("items", JArray(x :: Nil)) =>
                    val item = x.mapField {
                      case ("links", _) => "links" -> List(
                        ("rel" -> "section") ~~ ("name" -> "must") ~~ ("href" -> s"${uri.withPath(uri.path / "must")}"),
                        ("rel" -> "section") ~~ ("name" -> "must_not") ~~ ("href" -> s"${uri.withPath(uri.path / "must_not")}"),
                        ("rel" -> "section") ~~ ("name" -> "should") ~~ ("href" -> s"${uri.withPath(uri.path / "should")}")
                      ).++(fields.map { case (field, detail) =>
                        ("rel" -> "option") ~~
                          ("href" -> s"$uri/$field") ~~ ("name" -> field)
                          /*("data" -> List(
                            ("name" -> "field") ~~ ("value" -> s"$field")
                            //("name" -> "type") ~~ ("value" -> s"${(detail \ "type").extract[String]}")
                          ))*/
                      })
                      case y => y
                    }
                    ("items", JArray(item :: Nil))
                  case x => x
                })
              }
            }
          }
        }
      }
  }
}
