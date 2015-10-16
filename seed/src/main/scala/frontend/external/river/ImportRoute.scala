package frontend.external.river

import java.io.{ByteArrayInputStream, InputStreamReader}

import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import river.ami.XmlStt
import spray.http.HttpCharsets._
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{ContentTypeRange, HttpEntity}
import spray.httpx.unmarshalling.Unmarshaller
import spray.routing._
import spray.routing.authentication.{BasicAuth, UserPass}
import spray.util.LoggingContext
import akka.pattern._


import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }
import scala.xml._

/**
 * Created by henry on 10/5/15.
 */
trait ImportRoute extends HttpService {

  implicit def client: org.elasticsearch.client.Client

  def extractUser(userPass: UserPass): String = userPass.user
  val config = ConfigFactory.parseString("atlas = subaru")

  implicit private val log = LoggingContext.fromActorRefFactory(actorRefFactory)

  implicit val NodeSeqUnmarshaller =
    Unmarshaller[NodeSeq](ContentTypeRange(`application/xml`, `UTF-8`)) {
    case HttpEntity.NonEmpty(contentType, data) ⇒
      val parser = XML.parser
      XML.withSAXParser(parser).load(new InputStreamReader(new ByteArrayInputStream(data.toByteArray), contentType.charset.nioCharset))
    case HttpEntity.Empty ⇒ NodeSeq.Empty
  }

  lazy val `_import`: Route = {

      pathPrefix("_river" / "stt" / "ami" / Segment ) { id =>
          put {
            respondWithMediaType(`application/json`) {
              entity(as[NodeSeq]) { nodeSeq =>
                authenticate(BasicAuth(realm = "river", config, extractUser _)) { userName => implicit ctx =>

                  (nodeSeq \\ "Subject" filter { n => (n \ "@Name").text.toString == "RecognizeText" }) \ "Role" match {
                    case roles: Seq[Node] if roles.nonEmpty =>
                      log.info(s"---------${roles.getClass}")
                      log.info(s"roles: ${roles}")
                      actorRefFactory.actorOf(IndexLogRequest.props) ! roles
                    case other =>
                      log.warning(s"${ctx.request.method} ${ctx.request.uri} $other")
                      ctx.complete(BadRequest,
                      s"""{
                         |  "error" :
                         |  {
                         |    "title" : "",
                         |    "code" : "400",
                         |    "message" : "${other}"
                         |  }
                         |}
                       """.stripMargin)
                  }
                }
              }
            }
          } ~
          delete {
              complete(NoContent)
            }
       }
  }
}