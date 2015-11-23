package frontend.storedFilter

import elastic.ImplicitConversions._
import es.indices.logs
import frontend.{CollectionJsonSupport, ImplicitHttpServiceLogging}
import org.json4s.JsonDSL._
import org.json4s._
import spray.http.StatusCodes._
import spray.routing._

import scala.collection.JavaConversions._
import scalaz.OptionT._
import scalaz.Scalaz._
import scalaz._

trait StoredFilterRoute extends HttpService with CollectionJsonSupport with ImplicitHttpServiceLogging {

  implicit def client: org.elasticsearch.client.Client

  def fetchTypes: Directive1[List[String]] = onSuccess((for {
    template <- optionT(logs.getTemplate.asFuture.map(_.getIndexTemplates.headOption))
  } yield template.mappings.map(_.key).toList).run).flatMap {
    case Some(types) => provide(types)
    case _ => reject()
  }

  lazy val `_filter/`: Route =
    get {
      pathPrefix("_filter") {
          pathEnd {
            fetchTypes { types =>
              requestUri { uri =>
                collection { json =>
                  complete(OK, json ~~
                    ("links" -> types.map { x =>
                      ("name" -> x) ~~
                        ("href" -> s"${uri.withPath(uri.path / x)}")
                    }))
                }
              }
            }
          } ~
          pathPrefix(Segment){ typ =>
            pathEnd { implicit ctx =>
              actorRefFactory.actorOf(QueryRequest.props(typ))
            } ~
            pathPrefix(Segment) { id =>
              item(NewFilter("temporary")) { json =>
                complete(OK, json)
              }
            }
          }
      }
    } ~
    post {
      pathPrefix("_filter") {
        path(Segment) { s => implicit ctx =>
          actorRefFactory.actorOf(NewFilterRequest.props)
        }
      }
    }

}
