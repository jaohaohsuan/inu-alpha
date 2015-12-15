package frontend.external.river

import akka.actor.Props
import frontend.CollectionJsonSupport._
import frontend.PerRequest
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.update.UpdateResponse
import org.elasticsearch.client.Client
import river.ami.XmlStt
import spray.routing.RequestContext
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import river.ami.XmlStt
import scala.xml.{Elem, Node, NodeSeq}
import scala.util.{ Try, Success, Failure }
import spray.http.MediaTypes._
import akka.pattern._

case class Roles(values: Seq[Elem])

object IndexLogRequest {

  def props(id: String, index: String)(implicit ctx: RequestContext, client: Client) = {
    Props(classOf[IndexLogRequest], ctx, client, id, index)
  }
}

case class IndexLogRequest(ctx: RequestContext, implicit val client: Client, id: String, index: String) extends PerRequest {

  import context.dispatcher

  def processResult: Receive = {

    case r: UpdateResponse =>
      response {
        respondWithMediaType(`application/json`) {
          complete(OK, s"""{ "acknowledged": true, "created" : ${r.isCreated} }""")
        }
      }

    //警告：保存的json無法被保證欄位的正確性

    case Roles(values) =>
      def f: XmlStt = values.foldLeft(XmlStt())(_ append _).asResult
      Try(f) match {
        case Success(result) =>
          val doc = compact(render(result.body))
          client.prepareUpdate(s"logs-$index", "ami-l8k", id)
            .setDoc(doc).setUpsert(doc)
            .execute().future pipeTo self

        case Failure(ex) =>
          response {
            respondWithMediaType(`application/vnd.collection+json`) {
              requestUri { uri =>
                log.error(ex, s"$uri")
                complete(BadRequest,
                    s"""{
                        |  "error" :
                        |  {
                        |    "title" : "XPath",
                        |    "code" : "400",
                        |    "message" : "unexpected path found ${ex.getMessage}"
                        |  }
                        |}""".stripMargin)
              }
            }
          }
      }

    case dim: JObject =>
      val doc = compact(render(dim))
      client.prepareUpdate(s"logs-$index", "ami-l8k", id)
            .setDoc(doc).setUpsert(doc)
            .execute().future pipeTo self

  }
}
