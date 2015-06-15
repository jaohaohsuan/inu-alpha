package domain.search.template

import akka.actor.Props
import akka.contrib.pattern.ShardRegion
import akka.persistence.PersistentView


import org.elasticsearch.index.query.{BoolQueryBuilder, MatchQueryBuilder, QueryBuilder}
import org.elasticsearch.index.query.QueryBuilders._

object TemplateView {

  import domain.search.template.CommandQueryProtocol.{ Query }

  def props(): Props = Props[TemplateView]

  val idExtractor: ShardRegion.IdExtractor = {
    case m: Query => (m.templateId, m)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case m: Query => (math.abs(m.templateId.hashCode) % 100).toString
  }

  val shardName: String = "SearchTemplateView"

}

class TemplateView extends PersistentView {

  import TemplateState._
  import domain.search.template.CommandQueryProtocol._

  override val viewId: String = self.path.parent.name + "-" + self.path.name

  override val persistenceId: String = s"${Template.shardName}-${self.path.name}"

  //var boolQueryState = boolQuery()
  var clauses: Map[Int, BoolQueryClause] = Map.empty
  var name: String = ""
  var version: Int = 0

  import scala.language.implicitConversions

  implicit def asMatchQueryBuilderOperator(operator: String): MatchQueryBuilder.Operator =
    MatchQueryBuilder.Operator.valueOf(operator)

  def updateState(event: DomainEvent) = {

    event match {
      case Named(text) => name = text
      case ClauseAdded(_, clause) =>
        clauses = clauses + (clause.hashCode() -> clause)
      case ClauseRemoved(id, _) =>
        clauses = clauses - id
    }
  }

  def receive: Receive = {

    case event: DomainEvent =>
      updateState(event)
      version += 1

    case GetAsBoolClauseQuery(templateId) =>
      sender() ! BoolClauseResponse(templateId, name, clauses.values.toList, version)
    case GetVersion(templateId) =>
      sender() ! VersionResponse(templateId ,version)
  }

  def add(qb: BoolQueryBuilder, clause: BoolQueryClause): BoolQueryBuilder = {

    val query = clause match {
      case MatchClause(query, operator, _) =>
        matchQuery("dialogs.content", query).operator(operator)
      case SpanNearClause(terms, slop, inOrder, _) =>
        terms.foldLeft(slop.map { spanNearQuery().slop(_) }.getOrElse(spanNearQuery())){ (qb, term) =>
          qb.clause(spanTermQuery("dialogs.content", term)) }
          .inOrder(inOrder)
          .collectPayloads(false)
      case NamedBoolClause(_, _, _, clauses) =>
        clauses.foldLeft(boolQuery()){ (qb, e) =>
          add(qb, e)
        }
    }

    clause.occur match {
      case "must" =>
        qb.must(query)
      case "must_not" =>
        qb.mustNot(query)
      case "should" =>
        qb.should(query)
    }
  }
}
