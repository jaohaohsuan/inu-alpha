package routing

import akka.actor.{ActorRef, Props}
import net.hamnaberg.json.collection._
import spray.routing._
import spray.http.StatusCodes._
import util._
import scala.reflect.ClassTag
import scala.reflect._

object StoredQueryRoute {

  val OccurrenceRegex = """^must$|^must_not$|^should$""".r
  val BoolQueryClauseRegex = """^match$|^near$|^named$""".r

  case class NewTemplate(title: String)

  case class NamedClause(storedQueryId: String, storedQueryTitle: String, occurrence: String) {
    require(test)
    def test = occurrence.matches(OccurrenceRegex.toString())
  }

  case class MatchClause(query: String, operator: String, occurrence: String) {
    require(test)
    def test =
      operator.matches("^[oO][rR]$|^[Aa][Nn][Dd]$") && occurrence.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty
  }

  case class SpanNearClause(query: String,
                            slop: Option[Int],
                            inOrder: Boolean,
                            occurrence: String){
    require(test)
    def test = occurrence.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty && (slop.isEmpty || slop.get > 0)

  }
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
          implicit ctx => actorRefFactory.actorOf(Props(classOf[QueryStoredQueryItemsRequest], ctx, clusterClient, ""))
        } ~
        path("_query" / "template" / "search") {
          parameters('q) { q =>
            implicit ctx => actorRefFactory.actorOf(Props(classOf[QueryStoredQueryItemsRequest], ctx, clusterClient, q))
          }
        } ~
        pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
          pathEndOrSingleSlash {
            implicit ctx => actorRefFactory.actorOf(requestProps[GetStoredQueryItemRequest])
          } ~
            path( OccurrenceRegex ) { occurrence =>
              implicit ctx =>
                actorRefFactory.actorOf(requestProps[GetStoredQueryClausesRequest]) ! GetItemClauses(storedQueryId, occurrence)
            } ~
            pathPrefix( BoolQueryClauseRegex ) { clauseType =>
              pathEnd {
                URI { href =>
                  val template = Template(clauseType match {
                    case "match" => MatchClause("sample", "AND", "must")
                    case "near" => SpanNearClause("sample", Some(10), false, "should")
                    case "named" => NamedClause("12345", "sample", "must_not")
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
        put {
          pathPrefix("_query" / "template") {
            path(Segment) { implicit  storedQueryId =>
              entity(as[StoredQueryItem]) { entity => implicit  ctx => handle(entity)}
            }
          }
        } ~
        delete {
          import domain.StoredQueryAggregateRoot.RemoveClauses
          pathPrefix("_query" / "template" / Segment) { implicit storedQueryId =>
            path( BoolQueryClauseRegex / IntNumber) { (clauseType, clauseId) =>
              implicit ctx =>
                actorRefFactory.actorOf(requestProps[RemoveClauseRequest]) ! RemoveClauses(storedQueryId, List(clauseId))
            } ~
              path( OccurrenceRegex ) { occurrence =>
                implicit ctx =>
                  actorRefFactory.actorOf(requestProps[RemoveClauseRequest]) ! occurrence
              }
          }
        }
    }

  def handle(entity: AnyRef)(implicit storedQueryId: String, ctx: RequestContext): Unit = {

    import domain.StoredQueryAggregateRoot._

    val requestProps = Props(classOf[AddClauseRequest], ctx, clusterClient, storedQueryId)

    entity match {

      case StoredQueryItem(title, tags, _) =>
        actorRefFactory.actorOf(Props(classOf[UpdateRequest], ctx, clusterClient, storedQueryId, title, tags))

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
