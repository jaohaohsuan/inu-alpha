package read.storedQuery

import protocol.storedQuery.{BoolClause, MatchBoolClause, NamedBoolClause, SpanNearBoolClause, StoredQuery}
import scala.language.postfixOps
/**
  * Created by henry on 5/12/16.
  */
object QueryTerms {
  def unapply(arg: Any): Option[(String,List[String])] = {
    arg match {
      case StoredQuery(id, _, clauses, _) => Some((id, extract2(clauses.values).distinct))
      case _ => None
    }
  }

  def extract2(values: Iterable[BoolClause]): List[String] =
    values.flatMap({
      case MatchBoolClause(query, _, _, _) => query.split("""\s+""").toList
      case SpanNearBoolClause(terms, _, _, _, _) => terms
      case NamedBoolClause(_, _, _, clauses) => extract2(clauses.values)
    }) toList

//  def extract(clauses: Map[Int, BoolClause], zero: List[String] = Nil): List[String] = {
//    clauses.foldLeft(zero){ case (acc, (id, b)) =>
//      b match {
//        case MatchBoolClause(query, _, _, _) => acc ++ query.split("""\s+""").toList
//        case SpanNearBoolClause(terms, _, _, _, _) => acc ++ terms
//        case NamedBoolClause(_, _, _, `clauses`) => acc ++ extract(`clauses`, acc)
//        case _ => acc
//      }
//    }
//  }
}
