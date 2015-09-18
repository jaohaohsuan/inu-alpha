package read.storedQuery

import com.sksamuel.elastic4s._
import org.json4s.JsonAST.JObject
import protocol.storedQuery._


object BoolQueryConversion {

  type Fields = Map[String, Any]
  type Keywords = List[String]
  type ReferredClauses = List[String]
  type Id = String

  def buildBoolQuery(e: StoredQuery): (ReferredClauses, Keywords, BoolQueryDefinition) =
    e.clauses.values.foldLeft((List.empty[String],List.empty[String], new BoolQueryDefinition))(assemble)

  private def assemble(acc: (ReferredClauses, Keywords, BoolQueryDefinition), clause: BoolClause): (ReferredClauses, Keywords, BoolQueryDefinition) = {

    val (clausesTitle, keywords, bool, qd) = {
      val (clausesTitle, keywords, bool) = acc
      clause match {
        case MatchBoolClause(query, field, operator, _) =>
          (clausesTitle, keywords ++ query.split("""\s+"""), bool,
            new MultiMatchQueryDefinition(query).fields(field.replaceAll("""\s+""", "")).operator(operator.toUpperCase))
              //.matchType("best_fields")

        case SpanNearBoolClause(terms, field, slop, inOrder, _) =>
          val fields = """(agent|customer)""".r.findFirstIn(field).map { m => (0 to 2).map { n => s"$m$n" } }.getOrElse(Seq("dialogs"))

          val queries = fields.foldLeft(List.empty[QueryDefinition]) { (acc, field) => {
            terms.foldLeft(new SpanNearQueryDefinition().slop(slop)) { (qb, term) =>
              qb.clause(new SpanTermQueryDefinition(field, term))
            }.inOrder(inOrder).collectPayloads(false) :: acc
          }}
          (clausesTitle, keywords ++ terms , bool, new BoolQueryDefinition().should(queries))

        case NamedBoolClause(_, title, _, clauses) =>
          val (accClausesTitle, accKeywords, innerBool) = clauses.values.foldLeft((title :: clausesTitle, keywords, new BoolQueryDefinition))(assemble)
          (accClausesTitle, accKeywords , bool, innerBool)
      }
    }

    clause.occurrence match {
      case "must" => (clausesTitle, keywords, bool.must(qd))
      case "must_not" => (clausesTitle, keywords, bool.not(qd))
      case "should" => (clausesTitle, keywords, bool.should(qd).minimumShouldMatch(1))
    }
  }

  def unapply(value: AnyRef): Option[(QueryDefinition, JObject)] = try {
    value match {
      case e: StoredQuery =>
        import org.json4s.JsonDSL._
        import org.json4s.native.JsonMethods._

        val (referredClausesList: List[String], keywordsList: List[String], boolQuery) = buildBoolQuery(e)
        Some((boolQuery,
          ("query" -> parse(s"${boolQuery.builder}"))
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
