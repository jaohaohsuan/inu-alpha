package frontend.storedQuery.postRequest

import akka.actor.Props
import protocol.storedQuery.{StoredQuery, BoolClause}
import domain.storedQuery.StoredQueryAggregateRoot._
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes._
import spray.routing.RequestContext
import frontend.PerRequest
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

  import protocol.storedQuery.Exchange._
  import protocol.storedQuery._

  implicit val NamedClauseMonoid = new Monoid[NamedClause] {
    def as(c: NamedClause): BoolClause =
      NamedBoolClause(c.storedQueryId, c.storedQueryTitle, c.occurrence)
  }

  implicit val MatchClauseMonoid = new Monoid[MatchClause] {
    def as(c: MatchClause): BoolClause =
      MatchBoolClause(c.query, c.field, c.operator, c.occurrence)
  }

  implicit val SpanNearClauseMonoid = new Monoid[SpanNearClause] {
    def as(c: SpanNearClause): BoolClause =
      SpanNearBoolClause(c.query.split("""\s+""").toList, c.field, c.slop, c.inOrder, c.occurrence)
  }
}


object AddClauseRequest {

  def props[A: Monoid](entity: A)(implicit ctx: RequestContext, storedQueryId: String) = {
    val m = implicitly[Monoid[A]]
    Props(classOf[AddClauseRequest], ctx, storedQueryId, m.as(entity))
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




