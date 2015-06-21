package routing

import akka.actor.{ActorRef, Props}
import akka.contrib.pattern.ClusterClient.SendToAll
import domain.search.{StoredQueryRepo, StoredQueryCommandQueryProtocol}
import akka.pattern._
import net.hamnaberg.json.collection._

import spray.routing._
import spray.http.StatusCodes._
import util.CollectionJsonSupport

object StoredQueryRoute {

  sealed trait Entity

  case class NewTemplate(newName: String) extends Entity

  case class NamedClause(storedQueryId: String, name: String, occurrence: String) extends Entity

  case class MatchClause(query: String, operator: String, occurrence: String) extends Entity

  case class SpanNearClause(query: String,
                            slop: Option[Int],
                            inOrder: Boolean,
                            occurrence: String) extends Entity

}

trait StoredQueryRoute extends HttpService with CollectionJsonSupport {

  def clusterClient: ActorRef

  import StoredQueryRoute._
  import request.search._

  val URI = extract(ctx => java.net.URI.create(ctx.request.uri.toString))

  val queryTemplateRoute =
    get {
      path("_query" / "template") {
        implicit ctx =>
          actorRefFactory.actorOf(Props(classOf[GetStoredQueryItemsRequest], ctx, clusterClient))
      } ~
      pathPrefix("_query" / "template" / Segment) { storedQueryId =>
        pathEnd {
          URI { href =>

            val links = List(
                Link(href.resolve(s"$storedQueryId/must"), rel = "more", None, None, None),
                Link(href.resolve(s"$storedQueryId/must_not"), rel = "more", None, None, None),
                Link(href.resolve(s"$storedQueryId/should"), rel = "more", None, None, None)
            )

            complete(OK, JsonCollection(href, links, Item(href, List(Property("name", "temporary")), List.empty)))
          }
        } ~
        path( """^must$|^must_not$|^should$""".r) { occurrence =>
          implicit ctx =>
            actorRefFactory.actorOf(Props(classOf[GetClausesRequest], ctx, clusterClient, storedQueryId, occurrence))
        } ~
          path( """^match$|^near$|^named$""".r / IntNumber) { (clauseType, clauseId) =>
            implicit ctx =>
              complete(NoContent)
          }
      }
    } ~
      post {
        pathPrefix("_query" / "template" / Segment) { implicit templateId =>
          pathEnd {
            entity(as[NewTemplate]) { entity =>
              implicit ctx =>
                actorRefFactory.actorOf(Props(classOf[SaveAsNewRequest], ctx, clusterClient, templateId, entity.newName))
            }
          } ~ path("named") {
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
      delete {
        pathPrefix("_query" / "template" / Segment) { implicit templateId =>
          path( """^match$|^near$|^named$""".r / IntNumber) { (clauseType, clauseId) =>
            implicit ctx => actorRefFactory.actorOf(Props(classOf[RemoveClauseRequest], ctx, clusterClient, templateId, clauseType, clauseId))
          }
        } ~
          path("_query" / "template" / Segment / """^must$|^must_not$|^should$""".r) { (storedQueryId, occurrence) =>
            //delete all
            complete(NoContent)
          }

      }

  def handle(entity: Entity)(implicit templateId: String, ctx: RequestContext): Unit = {
    actorRefFactory.actorOf(Props(classOf[AddClauseRequest], ctx, clusterClient, templateId)) ! entity
  }
}
