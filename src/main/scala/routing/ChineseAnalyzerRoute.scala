package routing

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.GetDefinition
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexResponse
import org.json4s.{DefaultFormats, Formats}
import spray.http._
import spray.http.MediaTypes._
import spray.http.HttpHeaders._
import spray.httpx.Json4sSupport
import spray.httpx.marshalling.Marshaller
import spray.routing.HttpService
import spray.http.StatusCodes._
import util.{CorsSupport}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.util.{ Success, Failure}

object ChineseAnalyzerRoute {

  def retrieveDictionary(analyzerId: String): GetDefinition = {
    import com.sksamuel.elastic4s.ElasticDsl._
    get id "default" from "analyzers" / analyzerId
  }
}

case class ChineseData(dictionary: Option[String], stopwords: Option[String])
trait ChineseAnalyzerRoute extends HttpService with CorsSupport with Json4sSupport {

  import ChineseAnalyzerRoute._

  def node: Option[org.elasticsearch.node.Node]

  private lazy val elasticClient = node.map { com.sksamuel.elastic4s.ElasticClient.fromNode }.getOrElse(
    com.sksamuel.elastic4s.ElasticClient.remote("127.0.0.1", 9300)
  )

  // prefer UTF-8 encoding, but also render with other encodings if the client requests them
  implicit val StringMarshaller = stringMarshaller(`text/plain`)

  def stringMarshaller(contentType: ContentType, more: ContentType*): Marshaller[String] =
    Marshaller.of[String](contentType +: more: _*) { (value, contentType, ctx) ⇒
      ctx.marshalTo(HttpEntity(contentType, value))
    }

  def analyzerRoute = cors {
    head {
      path("_analyzer" / Segment / Segment) { (analyzerId, field) =>

        val getDict = elasticClient.execute { retrieveDictionary(analyzerId).fields("last-modified", "version") }

        onComplete(getDict){
          case Success(resp) =>
            val version = s"${resp.getField("version").getValue}"
            val clicks = resp.getField("last-modified").getValue.asInstanceOf[Long]

            println(s"etag:$version last-modified:$clicks")

            conditional(EntityTag(version), DateTime(clicks)) {
              println("changed")
              complete(OK)
            }

          case Failure(ex) => complete(InternalServerError, ex.getMessage)
        }
      }
    }
    get{
      path("_analyzer" / Segment / Segment) { (analyzerId, field) =>
        respondWithMediaType(`text/plain`) {

            val getDict = elasticClient.execute { retrieveDictionary(analyzerId) }

            onComplete(getDict){
              case Success(resp) =>
                val sourceMap = resp.getSourceAsMap()
                val clicks = s"${sourceMap.get("last-modified")}".toLong
                respondWithHeaders(ETag(s"${resp.getVersion}"), `Last-Modified`(spray.http.DateTime(clicks))) {
                  complete(s"${sourceMap.get(field)}")
                }
              case Failure(ex) => complete(InternalServerError, ex.getMessage)
            }
          }

      }
    } ~
    put {
      path("_analyzer" / Segment) { analyzerId =>
        entity(as[ChineseData]) { data =>
          
          import com.sksamuel.elastic4s.ElasticDsl._

          val indexing: Future[IndexResponse] =  elasticClient.execute { retrieveDictionary(analyzerId) }.flatMap { resp =>
              elasticClient.execute {
                index into "analyzers" / analyzerId id "default" fields appendToSource(resp, data)
              }
          }

          onComplete(indexing) {
            case Success(resp) =>
              complete(OK)
            case Failure(ex) =>
              complete(InternalServerError)
          }
        }
      }
    }
  }
  
  def appendToSource(resp: GetResponse, data: ChineseData) = {

    val appended = if (resp.isExists) {
      val source = resp.getSourceAsMap
      data.copy(
        dictionary = data.dictionary.map {
          _ + s"\n${source.get("dictionary")}"
        },
        stopwords = data.stopwords.map {
          _ + s"\n${source.get("stopwords")}"
        }
      )
    }
    else
      data

     List(
      appended.dictionary.map { ("dictionary", _) },
      appended.stopwords.map { ( "stopwords" ,_)},
      Some(("last-modified", org.joda.time.DateTime.now().getMillis))
    ).flatten
  }
}
