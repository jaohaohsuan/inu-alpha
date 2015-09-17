package frontend.storedQuery.postRequest

import akka.actor.Props
import protocol.storedQuery.{BoolClause, StoredQuery}
import domain.storedQuery.StoredQueryAggregateRoot._
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing.RequestContext
import frontend.PerRequest
import protocol.storedQuery.Terminology._
import scala.language.implicitConversions

object NewTemplateImplicits {
  implicit def optionStringToSet(value : Option[String]) = value.map { _.split("""\s+""").toSet }.getOrElse(Set.empty)
}

case class NewTemplate(title: String, tags: Option[String]){
  require( title.nonEmpty )
}

object NewTemplateRequest {
  def props(implicit ctx: RequestContext, e: NewTemplate, referredId: Option[String] = None) =
    Props(classOf[NewTemplateRequest], ctx, e, referredId)
}
case class NewTemplateRequest(ctx: RequestContext, e: NewTemplate, referredId: Option[String] = None) extends PerRequest {

  import NewTemplateImplicits._

  context.actorSelection("/user/aggregateRootProxy") ! CreateNewStoredQuery(e.title, referredId, e.tags)

  def processResult = {
    case ItemCreated(StoredQuery(id, title, _, _), _)  =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$id".replaceAll("""/(\d|temporary)+(?=/\d)""", ""))){
            complete(Created)
          }
        }
      }
  }
}

trait Monoid[A] {
  def as(clause :A ): BoolClause
}

object Monoid {

  import AddClauseRequest._
  import protocol.storedQuery._

  implicit val NamedClauseMonoid = new Monoid[NamedClause] {
    def as(clause: NamedClause): BoolClause =
      NamedBoolClause(clause.storedQueryId, clause.storedQueryTitle, clause.occurrence)
  }

  implicit val MatchClauseMonoid = new Monoid[MatchClause] {
    def as(clause: MatchClause): BoolClause =
      MatchBoolClause(clause.query, clause.field, clause.operator, clause.occurrence)
  }
}


object AddClauseRequest {

  def props[A: Monoid](entity: A)(implicit ctx: RequestContext, storedQueryId: String) = {

    val m = implicitly[Monoid[A]]
    Props(classOf[AddClauseRequest], ctx, storedQueryId, m.as(entity))
  }

  def singleField(field: String) = """\s+""".r.findFirstIn(field).isEmpty
  def queryFieldConstrain(field: String) = field.matches("""^dialogs$|^agent\*$|^customer\*$""")

  case class NamedClause(storedQueryId: String, storedQueryTitle: String, occurrence: String) {
    require(test)
    def test = occurrence.matches(OccurrenceRegex.toString())
  }

  case class MatchClause(query: String, field: String, operator: String, occurrence: String) {
    require(test)
    require(singleField(field), s"single field only")
    require(queryFieldConstrain(field), s"field only can be 'dialogs' or 'agent*' or 'customer*'")
    def test = operator.matches("^[oO][rR]$|^[Aa][Nn][Dd]$") && occurrence.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty

  }

  case class SpanNearClause(query: String, field: String, slop: Int, inOrder: Boolean, occurrence: String){
    require(test)
    require(field.nonEmpty)
    require(singleField(field), s"single field only")
    require(queryFieldConstrain(field), s"field only can be 'dialogs' or 'agent*' or 'customer*'")
    def test = occurrence.matches(OccurrenceRegex.toString()) && !query.trim.isEmpty
  }
}

case class AddClauseRequest(ctx: RequestContext, storedQueryId: String, clause: BoolClause) extends PerRequest {

  context.actorSelection("/user/aggregateRootProxy") ! AddClause(storedQueryId, clause)

  def processResult = {

    case ClauseAddedAck(clauseId) =>
      response {
        URI { href =>
          respondWithHeader(RawHeader("Location", s"$href/$clauseId")){
            complete(Created)
          }
        }
      }

    case CycleInDirectedGraphError =>
      response {
        complete(NotAcceptable)
      }
  }
}




