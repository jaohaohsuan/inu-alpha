package domain.search

sealed trait BoolQueryClause {
  def occurrence: String
}

case class NamedBoolClause(storedQueryId: String,
                           occurrence: String,
                           storedQueryName: Option[String] = None,
                           clauses: List[BoolQueryClause] = List.empty) extends BoolQueryClause {
 
  override def hashCode(): Int = storedQueryId.hashCode
}
case class MatchClause(query: String, 
                       operator: String, 
                       occurrence: String) extends BoolQueryClause

case class SpanNearClause(terms: List[String], 
                          slop: Option[Int], 
                          inOrder: Boolean, 
                          occurrence: String) extends BoolQueryClause

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
