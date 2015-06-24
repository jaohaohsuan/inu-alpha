package routing

import akka.actor.{ActorRef, Props}
import net.hamnaberg.json.collection._
import spray.routing._
import spray.http.StatusCodes._
import spray.http.{ AllOrigins }
import util._
import scala.reflect.ClassTag
import scala.reflect._

object StoredQueryRoute {

  sealed trait Entity

  case class NewTemplate(title: String) extends Entity

  case class NamedClause(storedQueryId: String, storedQueryTitle: String, occurrence: String) extends Entity

  case class MatchClause(query: String, operator: String, occurrence: String) extends Entity

  case class SpanNearClause(query: String,
                            slop: Option[Int],
                            inOrder: Boolean,
                            occurrence: String) extends Entity
}

trait StoredQueryRoute extends HttpService with CollectionJsonSupport with CorsSupport {

  def clusterClient: ActorRef

  import StoredQueryRoute._
  import request.search._
  import domain.StoredQueryItemsView._

  val URI = extract(ctx => java.net.URI.create(ctx.request.uri.toString))

  def requestProps[T <: akka.actor.Actor: ClassTag](implicit storedQueryId: String, ctx: RequestContext) =
    Props(classTag[T].runtimeClass, ctx, clusterClient, storedQueryId)

  def queryTemplateRoute =
    cors {
      get {
        path("_query" / "template") {
          implicit ctx => actorRefFactory.actorOf(Props(classOf[QueryStoredQueryItemsRequest], ctx, clusterClient))
        } ~
          pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
            pathEndOrSingleSlash {
              implicit ctx => actorRefFactory.actorOf(requestProps[GetStoredQueryItemRequest])
            } ~
              path( """^must$|^must_not$|^should$""".r) { occurrence =>
                implicit ctx =>
                  actorRefFactory.actorOf(requestProps[GetStoredQueryClausesRequest]) ! GetItemClauses(storedQueryId, occurrence)
              } ~
              pathPrefix( """^match$|^near$|^named$""".r ) { clauseType =>
                pathEnd {
                  URI { href =>
                    val template = Template(clauseType match {
                      case "match" => MatchClause("", "", "")
                      case "near" => SpanNearClause("", Some(10), false, "")
                      case "named" => NamedClause("", "", "")
                    })
                    complete(OK, JsonCollection(href, List.empty, List.empty, List.empty, Some(template)))
                  }
                } ~
                  path( IntNumber ) { clauseId =>
                    implicit ctx =>
                      complete(NoContent)
                  }
              }
          }
      } ~
        post {
          pathPrefix("_query" / "template") {
            /*pathEnd { implicit ctx =>
              entity(as[NewTemplate]) { entity => implicit ctx => handle(entity)("temporary", ctx) }
            } ~*/
            pathPrefix(Segment) { implicit storedQueryId =>
              pathEnd {
                //save as new
                entity(as[NewTemplate]) { entity => implicit ctx => handle(entity) }
              } ~ path("named") {
                entity(as[NamedClause]) { entity => implicit ctx => handle(entity) }
              } ~ path("match") {
                entity(as[MatchClause]) { entity => implicit ctx => handle(entity) }
              } ~ path("near") {
                entity(as[SpanNearClause]) { entity => implicit  ctx => handle(entity) }
              }
            }
          }
        } ~
        delete {
          import domain.StoredQueryAggregateRoot.RemoveClauses
          pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
            path( """^match$|^near$|^named$""".r / IntNumber) { (clauseType, clauseId) =>
              implicit ctx =>
                actorRefFactory.actorOf(requestProps[RemoveClauseRequest]) ! RemoveClauses(storedQueryId, List(clauseId))
            } ~
              path( """^must$|^must_not$|^should$""".r ) { occurrence =>
                implicit ctx =>
                  actorRefFactory.actorOf(requestProps[RemoveClauseRequest]) ! occurrence
              }
          }
        }
    }

  def handle(entity: Entity)(implicit storedQueryId: String, ctx: RequestContext): Unit = {

    import domain.StoredQueryAggregateRoot._

    val requestProps = Props(classOf[AddClauseRequest], ctx, clusterClient, storedQueryId)

    entity match {

      case SpanNearClause(query, slop, inOrder, occurrence) =>
        actorRefFactory.actorOf(requestProps) ! SpanNearBoolClause(query.split(" ").toList, slop, inOrder, occurrence)

      case NamedClause(referredId, title, occurrence) =>
        actorRefFactory.actorOf(requestProps) ! NamedBoolClause(referredId, title, occurrence)

      case MatchClause(query, operator, occurrence) =>
        actorRefFactory.actorOf(requestProps) ! MatchBoolClause(query, operator, occurrence)

      case NewTemplate(title) =>
        actorRefFactory.actorOf(Props(classOf[SaveAsNewRequest], ctx, clusterClient, storedQueryId, title))

      case _ => complete(BadRequest)
    }
  }
}
