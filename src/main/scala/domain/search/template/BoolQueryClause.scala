package domain.search.template

sealed trait BoolQueryClause {
  def occur: String
}

//case class TemplateClause(templateId: String, id: Option[Int], occur: String) extends BoolQueryClause

case class NamedBoolClause(templateId: String, templateName: String = "", clauses: List[BoolQueryClause] = List.empty, occur: String) extends BoolQueryClause {
  override def hashCode(): Int = templateId.hashCode

}
case class MatchClause(query: String, operator: String, occur: String) extends BoolQueryClause
case class SpanNearClause(terms: List[String], slop: Option[Int], inOrder: Boolean, occur: String) extends BoolQueryClause

/*
"""
|"query" : {
 |      "filtered" : {
 |         "filter" : {
 |            "bool" : {
 |              "should" : [
 |                { "term" : {"productID" : "KDKE-B-9947-#kL5"}},
 |                { "bool" : {
 |                  "must" : [
 |                    { "term" : {"productID" : "JODL-X-1937-#pV7"}},
 |                    { "term" : {"price" : 30}}
 |                  ]
 |                }}
 |              ]
 |           }
 |         }
 |      }
 |   }
"""*/
