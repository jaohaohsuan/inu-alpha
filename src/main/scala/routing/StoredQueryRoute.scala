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

  def singleField(field: String) = """\s+""".r.findFirstIn(field).isEmpty
  def queryFieldConstrain(field: String) = field.matches("""^dialogs$|^agent\*$|^customer\*$""")
  

  case class NewTemplate(title: String, tags: Option[String]){
    require( title.nonEmpty )
  }

  case class NamedClause(storedQueryId: String, storedQueryTitle: String, occurrence: String) {
    require(test)
    def test = occurrence.matches(OccurrenceRegex.toString())
  }

  case class MatchClause(query: String, field: String, operator: String, occurrence: String) {
    require(test)
    require(singleField(field), s"single field only")
    require(queryFieldConstrain(field), s"field only can be 'dialogs' or 'agent*' or 'customer*'")
    def test =
      operator.matches("^[oO][rR]$|^[Aa][Nn][Dd]$") && occurrence.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty

  }

  case class SpanNearClause(query: String,
                            field: String,
                            slop: Int,
                            inOrder: Boolean,
                            occurrence: String){
    require(test)
    require(field.nonEmpty)
    require(singleField(field), s"single field only")
    require(queryFieldConstrain(field), s"field only can be 'dialogs' or 'agent*' or 'customer*'")
    def test = occurrence.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty
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
          implicit ctx => actorRefFactory.actorOf(Props(classOf[QueryStoredQueryItemsRequest], ctx, clusterClient, None, None))
        } ~
        path("_query" / "template" / "search") {
          parameters('q.?, 'tags.? ) { (q, tags) =>
            implicit ctx => actorRefFactory.actorOf(Props(classOf[QueryStoredQueryItemsRequest], ctx, clusterClient, q, tags))
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

                  def asPropertyList(cc: AnyRef) =
                    (List[ValueProperty]() /: cc.getClass.getDeclaredFields) {(acc, f) =>
                      f.setAccessible(true)
                      val field = f.getName

                      val prompt = field match {
                        case "field" =>Some("dialogs agent* customer*")
                        case _ =>   None
                      }

                      val v:Any = f.get(cc)

                      v match {
                        case i: Int => ValueProperty(field, prompt, Some(i)) :: acc
                        case b: Boolean => ValueProperty(field, prompt, Some(b)) :: acc
                        case l: Long => ValueProperty(field, prompt, Some(l)) :: acc
                        case _ => ValueProperty(field, prompt, Some(s"${f.get(cc)}")) :: acc
                      }
                    }

                  val template = clauseType match {
                    case "match" => Template(asPropertyList(MatchClause("sample", "dialogs", "AND", "must")))
                    case "near" => Template(asPropertyList(SpanNearClause("sample", "dialogs" ,10, false, "should")))
                    case "named" => Template(NamedClause("12345", "sample", "must_not"))
                  }
                  complete(OK, JsonCollection(href, List.empty, List.empty, List.empty, Some(template)))
                }
              } ~
                path( IntNumber ) { clauseId =>
                  implicit ctx =>
                    complete(NoContent)
                }
            } ~
            pathPrefix("preview") {
              pathEnd {
                implicit ctx => actorRefFactory.actorOf(Props(classOf[PreviewRequest], ctx, clusterClient, storedQueryId))
              } ~ path("status") {
                implicit ctx => actorRefFactory.actorOf(Props(classOf[LteCountRequest], ctx, clusterClient, storedQueryId))
              }
            }
        }
      } ~
        post {
          pathPrefix("_query" / "template") {
            pathEnd {
              entity(as[NewTemplate]) { entity => implicit ctx => handle(entity)("", ctx) }
            } ~
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

      case SpanNearClause(query, fields, slop, inOrder, occurrence) =>
        actorRefFactory.actorOf(requestProps) ! SpanNearBoolClause(query.split("""\s+""").toList, fields, slop, inOrder, occurrence)

      case NamedClause(referredId, title, occurrence) =>
        actorRefFactory.actorOf(requestProps) ! NamedBoolClause(referredId, title, occurrence)

      case MatchClause(query, field, operator, occurrence) =>
        actorRefFactory.actorOf(requestProps) ! MatchBoolClause(query, field, operator, occurrence)

      case NewTemplate(title, tags) =>
        actorRefFactory.actorOf(Props(classOf[SaveAsNewRequest], ctx, clusterClient, Option(storedQueryId).filter(_.trim.nonEmpty), title, tags))

      case _ => complete(BadRequest)
    }
  }
}
