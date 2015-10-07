package read.storedQuery

import org.elasticsearch.index.query.{MatchQueryBuilder, QueryBuilder, BoolQueryBuilder}
import org.elasticsearch.index.query.QueryBuilders._
import org.json4s.JsonAST.JObject
import protocol.storedQuery._
import protocol.storedQuery.{BoolClause, StoredQuery}


object BoolQueryConversion {

  type Fields = Map[String, Any]
  type Keywords = List[String]
  type ReferredClauses = List[String]
  type Id = String

  def buildBoolQuery(e: StoredQuery): (ReferredClauses, Keywords, BoolQueryBuilder) =
    e.clauses.values.foldLeft((List.empty[String],List.empty[String], boolQuery()))(assemble)

  private def assemble(acc: (ReferredClauses, Keywords, BoolQueryBuilder), clause: BoolClause): (ReferredClauses, Keywords, BoolQueryBuilder) = {

    val (clausesTitle, keywords, bool, qd) = {
      val (clausesTitle, keywords, bool) = acc
      clause match {
        case MatchBoolClause(query, field, operator, _) =>
          (clausesTitle, keywords ++ query.split("""\s+"""), bool,
            multiMatchQuery(query, field.replaceAll("""\s+""", "")).operator(MatchQueryBuilder.Operator.valueOf(operator)))
              //.matchType("best_fields")

        case SpanNearBoolClause(terms, field, slop, inOrder, _) =>
          val fields = """(agent|customer)""".r.findFirstIn(field).map { m => (0 to 2).map { n => s"$m$n" } }.getOrElse(Seq("dialogs"))

          val queries = fields.foldLeft(boolQuery()) { (acc, field) => {
            acc.should(terms.foldLeft(spanNearQuery().slop(slop)) { (qb, term) =>
              qb.clause(spanTermQuery(field, term))
            }.inOrder(inOrder).collectPayloads(false))
          }}
          (clausesTitle, keywords ++ terms , bool, queries)

        case NamedBoolClause(_, title, _, clauses) =>
          val (accClausesTitle, accKeywords, innerBool) = clauses.values.foldLeft((title :: clausesTitle, keywords, boolQuery()))(assemble)
          (accClausesTitle, accKeywords , bool, innerBool)
      }
    }

    clause.occurrence match {
      case "must" => (clausesTitle, keywords, bool.must(qd))
      case "must_not" => (clausesTitle, keywords, bool.mustNot(qd))
      case "should" => (clausesTitle, keywords, bool.should(qd).minimumNumberShouldMatch(1))
    }
  }

  def unapply(value: AnyRef): Option[(QueryBuilder, JObject)] = try {
    value match {
      case e: StoredQuery =>
        import org.json4s.JsonDSL._
        import org.json4s.native.JsonMethods._

        val (referredClausesList: List[String], keywordsList: List[String], boolQuery) = buildBoolQuery(e)
        Some((boolQuery,
          ("query" -> parse(s"$boolQuery"))
            ~ ("title" -> e.title)
            ~ ("tags" -> e.tags)
            ~ ("keywords" -> keywordsList)
            ~ ("referredClauses" -> referredClausesList)))

      case unknown => None
    }
  } catch {
    case ex: Exception => None
  }
}
