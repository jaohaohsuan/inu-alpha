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
import common.ImplicitPrint._
import text.ImplicitConversions._

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
      XML.withSAXParser(parser).load(new InputStreamReader(new ByteArrayInputStream(data.toByteArray), contentType.charset.nioCharset))
    case HttpEntity.Empty ⇒ NodeSeq.Empty
  }

  val agentRole = """[R,r]0""".r
  val customerRole = """[R,r]1""".r

  implicit class JValueExtended(value: JValue) {
    def has(childString: String): Boolean = {
      if ((value \ childString) != JNothing) {
        true
      } else {
        false
      }
    }
  }

  case class LogsDoc(agentParties:    Int = 0,
                     customerParties: Int = 0,
                     parties:         Map[String,String] = Map.empty, body: JObject = JObject(), mixed: List[(String, String, Int, Int, String)] = List.empty) {

    import org.json4s.JsonDSL._

    def sort() = {
      copy( mixed = mixed.sortBy { case (_, _, _, end, _) => end })
    }

    private def addField(field: String, default: JValue = JArray(List.empty)): LogsDoc = {
      body \ field match {
        case JNothing => copy(body = body ~ (field -> default))
        case _ => this
      }
    }

    private def transformToParty(who: String) = {
      who match {
        case r if agentRole.matches(r) && !parties.contains(r) =>
          val alias = s"agent$agentParties"
          copy(parties = this.parties + (r -> alias), agentParties = this.agentParties + 1).addField(alias)

        case r if customerRole.matches(r) && !parties.contains(r) =>
          val alias = s"customer$customerParties"
          copy(parties = this.parties + (r -> alias), customerParties = this.customerParties + 1).addField(alias)

        case _ => this
      }
    }

    def append(n: NodeSeq) = {

      val who = (n \ "@Name").text

      (n \ "EndPoint" \ "Item").foldLeft(transformToParty(who)){ (acc, item) =>

        val begin = (item \ "@Begin").text.toInt
        val end = (item \ "@End").text.toInt
        val content = (item \ "Text").text

        val newBody = acc.body transformField {
          case JField(name, JArray(arr)) if name == acc.parties(who) =>
            (name, JArray(arr.:+(JString(s"$name-$begin $content"))))
        }

        acc.copy(body = newBody.asInstanceOf[JObject], mixed = acc.mixed.:+((who, acc.parties(who), begin, end, content)))
      }
    }
  }

  lazy val `_import`: Route = {

      pathPrefix("_river" / "stt" / Segment / Segment ) { (provider, id) =>
          put {
            respondWithMediaType(`application/json`) {
              entity(as[NodeSeq]) { nodeSeq =>
                authenticate(BasicAuth(realm = "river", config, extractUser _)) { userName =>
                  val roles = (nodeSeq \\ "Subject" filter { n => (n \ "@Name").text.toString == "RecognizeText" }) \ "Role"
                  val doc = roles.foldLeft(LogsDoc()){ (acc, role) =>
                    acc.append(role)
                  }
                  log.info(s"$userName ${pretty(render(doc.body))}\n ${doc.sort().mixed}")
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