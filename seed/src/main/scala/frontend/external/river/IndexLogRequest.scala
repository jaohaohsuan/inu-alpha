package frontend.external.river

import akka.actor.Props
import frontend.PerRequest
import org.elasticsearch.client.Client
import river.ami.XmlStt
import spray.routing.RequestContext
import spray.http.StatusCodes._
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import river.ami.XmlStt
import scala.xml.NodeSeq

object IndexLogRequest {

  def props(implicit ctx: RequestContext, client: Client) = {
    Props(classOf[IndexLogRequest], ctx, client)
  }
}

case class IndexLogRequest(ctx: RequestContext, implicit val client: Client) extends PerRequest {

  def processResult: Receive = {
    case roles: NodeSeq =>
      val doc = roles.foldLeft(XmlStt())(_ append _).asResult().body

      log.info(s"${pretty(render(doc))}\n ")

      response {
        complete(OK, """{ "acknowledged": true }""")
      }
  }
}
