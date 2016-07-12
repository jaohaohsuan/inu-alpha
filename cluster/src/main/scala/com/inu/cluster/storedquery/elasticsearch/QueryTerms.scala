package com.inu.cluster.storedquery.elasticsearch

import com.inu.protocol.storedquery.messages._
import scala.language.postfixOps
/**
  * Created by henry on 5/12/16.
  */
object QueryTerms {
  def unapply(arg: Any): Option[(String,List[String])] = {
    arg match {
      case StoredQuery(id, _, clauses, _) => Some((id, extract(clauses.values).distinct))
      case _ => None
    }
  }

  def extract(values: Iterable[BoolClause]): List[String] =
    values.flatMap({
      case MatchClause(query, _, _ , _) =>  """[\w\u4e00-\u9fa5]+""".r findAllIn query
      case SpanNearClause(terms, _, _, _, _) =>  """[\w\u4e00-\u9fa5]+""".r findAllIn terms
      case NamedClause(_, _, _, clauses) => extract(clauses.getOrElse(Map.empty).values)
    }) toList
}
