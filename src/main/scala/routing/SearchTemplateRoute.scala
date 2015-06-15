package routing

import akka.actor.{ActorRef, Props}
import org.json4s.{Formats, DefaultFormats}
import routing.request.search.template.{AddClauseRequest, RemoveClauseRequest}
import spray.httpx.Json4sSupport

import spray.routing._

object SearchTemplateRoute {

  sealed trait Entity

  case class NewTemplate(newName: String) extends Entity

  case class NamedClause(clauseTemplateId: String, occur: String) extends Entity

  case class MatchClause(query: String, operator: String, occur: String) extends Entity

}

trait SearchTemplateRoute extends HttpService with Json4sSupport {

  def clusterClient: ActorRef

  import SearchTemplateRoute._

  //import util.CollectionJsonSupport._
  implicit def json4sFormats: Formats = DefaultFormats

  val queryTemplateRoute =
    post {
      pathPrefix("_query" / "template") {
        path(Segment) { implicit templateId =>
          entity(as[NewTemplate]) { entity =>
            implicit ctx =>
              handle(entity)
          }
        }
      }
    } ~
      post {
        pathPrefix("_query" / "template" / Segment) { implicit templateId =>
          path("named") {
            entity(as[NamedClause]) { entity =>
              implicit ctx => handle(entity)
            }
          } ~ path("match") {
            entity(as[MatchClause]) { entity =>
              implicit ctx => handle(entity)
            }
          }
        }
      } ~
      get {
        path("api") {
          complete("hello spray.io")
        }
      } ~
      delete {
        pathPrefix("_query" / "template" / Segment) { implicit templateId =>
          path( """^match$|^near$|^named$""".r / IntNumber) { (clauseType, clauseId) => {
            implicit ctx => actorRefFactory.actorOf(Props(classOf[RemoveClauseRequest], ctx, clusterClient, templateId, clauseType, clauseId))
          }
          }
        }
      }

  def handle(entity: Entity)(implicit templateId: String, ctx: RequestContext): Unit = {
    actorRefFactory.actorOf(Props(classOf[AddClauseRequest], ctx, clusterClient, templateId)) ! entity
  }
}
