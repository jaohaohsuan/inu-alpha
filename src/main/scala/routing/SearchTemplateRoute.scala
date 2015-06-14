package routing

import akka.actor.{ActorRef, Props}
import org.json4s.{Formats, DefaultFormats}
import spray.httpx.Json4sSupport

import spray.routing._

object SearchTemplateRoute {

  import routing.request.search.template._

  def props(entity: Entity, clusterClient: ActorRef)(implicit templateId: String, ctx: RequestContext): Props = {
    entity match {
      case MatchClause(query, operator, occur) =>
        Props(classOf[AddMatchClauseRequest], ctx, clusterClient, templateId, query, operator, occur)
      case NamedClause(clauseTemplateId, occur) =>
        Props(classOf[AddNamedClauseRequest], ctx, clusterClient, templateId, clauseTemplateId, occur)
      case NewTemplate(newName) =>
        Props(classOf[SaveAsNewRequest], ctx, templateId, newName)
    }
  }

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
            complete(spray.http.StatusCodes.OK)
          }
          }

        }

      }

  def handle(entity: Entity)(implicit templateId: String, ctx: RequestContext): Unit = {
    actorRefFactory.actorOf(SearchTemplateRoute.props(entity, clusterClient), s"$templateId-${entity.getClass.getSimpleName}")
  }
}
