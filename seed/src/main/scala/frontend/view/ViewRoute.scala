package frontend.view

import java.nio.charset.Charset

import akka.actor.Props
import frontend.CollectionJsonSupport._
import frontend.storedFilter.TemplateExtractor
import frontend.{ImplicitHttpServiceLogging, CollectionJsonSupport}
import org.elasticsearch.index.query.QueryBuilders
import spray.http.Uri.Path
import spray.routing._
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.collection.JavaConversions._
import spray.routing.authentication.BasicAuth

import scala.concurrent.Future


trait ViewRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging with TemplateExtractor {

  implicit def client: org.elasticsearch.client.Client

  private implicit class Sender0(props: Props) {
    def send = actorRefFactory.actorOf(props)
  }

  def count(labeledSources: (String, List[String])): Future[JValue] = {
    val (label, sources) = labeledSources
    client.prepareSearch("logs-*").setTypes(sources:_*).setSize(0).execute().future.map { r =>
      ("label" -> label) ~~ ("value" -> r.getHits.getTotalHits)
    }
  }

  private def furtherLinks(sources: Set[String]): Directive1[JValue] = requestUri.flatMap {
    case uri =>
      val count = ("href" -> s"${uri.withPath(uri.path / "count")}") ~~ ("rel" -> "contents") ~~ ("render" -> "graph")
      //val storedQuery = ("href" -> s"${uri.withPath(Path("/_query") / "template")}") ~~ ("rel" -> "service") ~~ ("render" -> "link")

      provide(JArray(count :: sources.map { src => ("href" -> s"${uri.withPath(uri.path / "filter" / src)}") ~~ ("rel" -> "section") }.toList))
  }

  lazy val `_view`: Route = pathPrefix("_view") {
    authenticate(BasicAuth("logs")) { usrCtx =>
        template { sources =>
          dataSources(usrCtx.username)(sources.keySet) { authorizedDs =>

          val srcPathRegex = authorizedDs.map(_.formatted( """^%s$""")).mkString("|").r
            pathEnd {
              collection { json =>
                furtherLinks(authorizedDs) { links =>
                  val body = json.mapField {
                    case ("links", _) => ("links", links)
                    case x => x
                  }
                  respondWithMediaType(`application/vnd.collection+json`) {
                    complete(OK, body)
                  }
                }
              }
            } ~
            path("count") {
              val logTypes = authorizedDs.foldLeft(("total", authorizedDs.toList) :: Nil) { (acc, e) => (e, e :: Nil) :: acc }
              onSuccess(Future.traverse(logTypes)(count)){ sourcesCount =>
                complete(OK, ("key" -> "datasource") ~~ ("values" -> JArray(sourcesCount)))
              }
            } ~
            pathPrefix("filter" / srcPathRegex) { src =>
              pathEnd { implicit ctx =>
                QueryFilterRequest.props.send
              } ~
              path(Segment) { id =>
                put {
                  complete(OK)
                } ~
                delete {
                  complete(NoContent)
                }
              }
            }
          }
        }

    }
  }

}
