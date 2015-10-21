package frontend.external.river

import java.io.{ByteArrayInputStream, InputStreamReader}

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.JsonMethods._
import river.ami.XmlStt
import spray.http.HttpCharsets._
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{ContentTypeRange, HttpEntity}
import spray.httpx.Json4sSupport
import spray.httpx.unmarshalling.Unmarshaller
import spray.routing._
import spray.routing.authentication.{BasicAuth, UserPass}
import spray.util.LoggingContext
import akka.pattern._
import org.json4s.native.JsonMethods._
import org.json4s._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Try, Success, Failure }
import scala.xml._

/**
 * Created by henry on 10/5/15.
 */
trait ImportRoute extends HttpService with Json4sSupport {

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

  var handleAllExceptions = ExceptionHandler {
    case ex: Exception =>
      log.error(ex, "MatchError")
      complete(BadRequest, s"""{
                              |  "error" :
                              |  {
                              |    "title" : "Invalid id format",
                              |    "code" : "400",
                              |    "message" : "${ex.getMessage()}, please follow the pattern '[YYYYMMDDhhmmssSSS]['I'][RECType][CustomerID][SerialNumber] '"
                              |  }
                              |}
                     """.stripMargin)
  }

  def datetimeExtractorDirective(id: String): Directive1[() => String] = {
    val file = """(\d{17})I(\d{3})(\d{3})(\d{4})""".r
    def idExtractor(): String = id match {
      case file(datetime, _, _ ,_) =>
        val yyyyMMddHHmmssSSS = org.joda.time.format.DateTimeFormat.forPattern("yyyyMMddHHmmssSSS")
        yyyyMMddHHmmssSSS.parseDateTime(datetime).toString("yyyy.MM.dd")
    }
    provide(idExtractor _)
  }

  lazy val `_import`: Route = {
    path("_river" / "stt" / "ami" / Segment ) { id =>
      handleExceptions(handleAllExceptions){
        datetimeExtractorDirective(id) { getIndex =>
          put {
            respondWithMediaType(`application/json`) {
              entity(as[NodeSeq]) { nodeSeq =>
                authenticate(BasicAuth(realm = "river", config, extractUser _)) { userName => implicit ctx =>
                  val node = (nodeSeq \\ "Subject" find { n => (n \ "@Name").text == "RecognizeText" })
                    .map(_.child.collect { case e: Elem => e })
                  node match {
                    case Some(roles) =>
                      actorRefFactory.actorOf(IndexLogRequest.props(id, s"${getIndex()}")) ! roles
                    case None =>
                      ctx.complete(BadRequest,
                        s"""{
                             |  "error" :
                             |  {
                             |    "title" : "XPath",
                             |    "code" : "400",
                             |    "message" : "unexpected path found"
                             |  }
                             |}
                     """.stripMargin)
                    }
                  }
                }
              }
          } ~
          delete {
              //onComplete(client.prepareDelete().setIndex(s"logs-$"))
            complete(NoContent)
          }
        }
      }
    } ~
    path("_river" / "dim" / "LOG8000" / Segment ) { id =>
      handleExceptions(handleAllExceptions){
        datetimeExtractorDirective(id) { getIndex =>
          put {
            authenticate(BasicAuth(realm = "river", config, extractUser _)) { userName =>
              entity(as[JObject]) { obj => implicit ctx =>
                //log.info(s"$obj")
                ctx.complete(OK)
              }
            }
          }
        }
      }
    }
  }
}