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

  var boolQueryState = boolQuery()
  var clauses: Map[Int, BoolQueryClause] = Map.empty
  var name: String = ""

  import scala.language.implicitConversions

  implicit def asMatchQueryBuilderOperator(operator: String): MatchQueryBuilder.Operator =
    MatchQueryBuilder.Operator.valueOf(operator)

  def receive: Receive = {

    case Named(text) => name = text
    case ClauseAdded(_, clause) =>
      boolQueryState = add(boolQueryState, clause)
      clauses = clauses + (clause.hashCode() -> clause)
    case GetAsBoolClauseQuery(templateId) =>
      sender() ! BoolClauseResponse(templateId, name, clauses.values.toList)
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
      case NamedBoolClause(_,_, clauses, occur) =>
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
