package worker.percolator.mappings

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.WhitespaceAnalyzer
import com.sksamuel.elastic4s.mappings.FieldType._

object fields {
  val customAnalyzer = WhitespaceAnalyzer

  val title = "title" typed StringType index "not_analyzed"
  val referredClauses = "referredClauses" typed StringType analyzer customAnalyzer
  val tags = "tags" typed StringType analyzer customAnalyzer nullValue "" includeInAll false
  val enabled = "enabled" typed BooleanType index "not_analyzed" includeInAll false
  val keywords = "keywords" typed StringType analyzer customAnalyzer nullValue ""
}
