package frontend.external.river

import akka.actor.Props
import frontend.PerRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.client.Client
import river.ami.XmlStt
import spray.routing.RequestContext
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import river.ami.XmlStt
import scala.xml.{Elem, Node, NodeSeq}
import scala.util.{ Try, Success, Failure }
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
    case r: IndexResponse =>
      response {
        complete(OK, s"""{ "acknowledged": true, "created" : ${r.isCreated} }""")
      }

    //警告：保存的json無法被保證欄位的正確性

    case Roles(values) =>
      def f: XmlStt = values.foldLeft(XmlStt())(_ append _).asResult
      Try(f) match {
        case Success(doc) =>
          client.prepareIndex(s"logs-$index", "ami-l8k")
            .setId(id)
            .setSource(s"${compact(render(doc.body))}")
            .execute().asFuture pipeTo self

        case Failure(ex) => response {
          requestUri { uri =>
            log.error(ex, s"$uri")
            complete(BadRequest, s"""{
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
}
