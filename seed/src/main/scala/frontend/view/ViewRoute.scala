package frontend.view

import akka.actor.Props
import frontend.storedFilter.TemplateExtractor
import frontend.{ImplicitHttpServiceLogging, CollectionJsonSupport}
import org.json4s.JsonAST._
import spray.routing._
import spray.http.StatusCodes._
import elastic.ImplicitConversions._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import scala.concurrent.Future


trait ViewRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging with TemplateExtractor {

  implicit def client: org.elasticsearch.client.Client

  private implicit class Sender0(props: Props) {
    def send = actorRefFactory.actorOf(props)
  }

  def count(source: String): Future[(String, JValue)] =
    client.prepareCount("logs-*").setTypes(source).execute().future.map{ r => source -> r.getCount}


  lazy val `_view`: Route = pathPrefix("_view") {
    get {
      template { sources =>
        collection { json =>
          onSuccess(Future.traverse(sources.keys)(count)){ sourceCount =>

            val body = json.mapField {
              case ("items", _) => ("items", JObject(sourceCount.toList).asTemplate :: Nil )
              case x => x
            }

            complete(OK, body)
          }
        }
      }
    }
  }



}
