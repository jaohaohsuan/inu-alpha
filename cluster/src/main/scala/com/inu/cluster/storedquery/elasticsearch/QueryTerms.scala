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
      case MatchClause(query, _, _ , _) => query.split("""\s+""").toList
      case SpanNearClause(terms, _, _, _, _) => terms.split("""\s+""").toList
      case NamedClause(_, _, _, clauses) => extract(clauses.getOrElse(Map.empty).values)
    }) toList
}
