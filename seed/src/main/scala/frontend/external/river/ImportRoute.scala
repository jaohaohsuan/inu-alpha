package frontend.external.river

import java.io.{ByteArrayInputStream, InputStreamReader}

import com.typesafe.config.ConfigFactory
import spray.http.{ContentTypeRange, HttpEntity}
import spray.httpx.unmarshalling.Unmarshaller
import spray.routing._
import spray.http.StatusCodes._
import spray.http.MediaTypes._
import spray.routing.authentication.{BasicAuth, UserPass}
import spray.util.LoggingContext
import scala.concurrent.Future
import scala.xml._
import spray.http.HttpCharsets._
import org.json4s
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by henry on 10/5/15.
 */
trait ImportRoute extends HttpService {

  def extractUser(userPass: UserPass): String = userPass.user
  val config = ConfigFactory.parseString("atlas = subaru")

  implicit private val log = LoggingContext.fromActorRefFactory(actorRefFactory)

  implicit val NodeSeqUnmarshaller =
    Unmarshaller[NodeSeq](ContentTypeRange(`application/xml`, `UTF-8`)) {
    case HttpEntity.NonEmpty(contentType, data) ⇒
      val parser = XML.parser
      try {
        parser.setProperty("http://apache.org/xml/properties/locale", java.util.Locale.ROOT)
      } catch {
        case e: org.xml.sax.SAXNotRecognizedException ⇒ // property is not needed
      }
      XML.withSAXParser(parser).load(new InputStreamReader(new ByteArrayInputStream(data.toByteArray), contentType.charset.nioCharset))
    case HttpEntity.Empty ⇒ NodeSeq.Empty
  }

  lazy val `_import`: Route = {

      pathPrefix("_river" / "stt" / Segment / Segment ) { (provider, id) =>
          put {
            respondWithMediaType(`application/json`) {
              entity(as[NodeSeq]) { nodeSeq =>
                authenticate(BasicAuth(realm = "river", config, extractUser _)) { userName =>
                  val roles = (nodeSeq \\ "Subject" filter { n => (n \ "@Name").text.toString == "RecognizeText" }) \ "Role"
                  roles.foldLeft(JObject()){ (acc, n) =>
                    val item = (n \\ "Item")
                    acc
                  }
                  log.info(s"$userName ${roles}")
                  complete(OK, """{ "acknowledged": true }""")
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
