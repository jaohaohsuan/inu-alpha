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

                  val file = """(\d{17})I(\d{3})(\d{3})(\d{4})""".r
                  def idExtractor: DateTime = id match {
                    case file(datetime, _, _ ,_) =>
                      val yyyyMMddHHmmssSSS = org.joda.time.format.DateTimeFormat.forPattern("yyyyMMddHHmmssSSS")
                      yyyyMMddHHmmssSSS.parseDateTime(datetime)
                  }

                  Try(idExtractor) match {
                    case Success(dateTime) =>
                      val node = (nodeSeq \\ "Subject" find { n => (n \ "@Name").text == "RecognizeText" })
                        .map(_.child.collect { case e: Elem => e })
                      node match {
                        case Some(roles) =>
                          actorRefFactory.actorOf(IndexLogRequest.props(id, s"${dateTime.toString("yyyy.MM.dd")}")) ! roles
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
                    case Failure(ex) =>
                      log.error(ex, s"${ctx.request.uri}")
                      ctx.complete(BadRequest, s"""{
                                                  |  "error" :
                                                  |  {
                                                  |    "title" : "id extracting",
                                                  |    "code" : "400",
                                                  |    "message" : "${ex.getMessage}"
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