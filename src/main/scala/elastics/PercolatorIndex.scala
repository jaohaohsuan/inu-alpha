package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.mappings.MappingDefinition
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.support.master.AcknowledgedResponse

import scala.concurrent.Future

object PercolatorIndex {

  val `inu-percolate` = "inu-percolate"

  val queryTemplateFieldsAnalyzer = WhitespaceAnalyzer

  object fields {

    val title = "title" typed StringType index "not_analyzed"
    val referredClauses = "referredClauses" typed StringType analyzer queryTemplateFieldsAnalyzer
    val tags = "tags" typed StringType analyzer queryTemplateFieldsAnalyzer nullValue "" includeInAll false
    val enabled = "enabled" typed BooleanType index "not_analyzed" includeInAll false
    val keywords = "keywords" typed StringType analyzer queryTemplateFieldsAnalyzer nullValue ""
    
    def `dialogs, agent*, customer*`(analyzer: String) = {

      import LteTemplate.fields._

      def field(field: String) =
        configIndexAnalyzer(field typed StringType, analyzer)

      (0 to 5).foldLeft(List(configIndexAnalyzer(dialogs, analyzer))){ (acc, n) =>
        field(s"customer$n") :: field(s"agent$n") :: acc
      }
    }
  }

  val `inu-percolate/.percolator` = IndexType(`inu-percolate`, ".percolator")
}

trait PercolatorIndex extends util.ImplicitActorLogging{
  self: Actor ⇒

  lazy val `PUT inu-percolate` = {
    import PercolatorIndex._
    import context.dispatcher
    client.execute { index exists `inu-percolate` }.flatMap { resp =>
      if (!resp.isExists)
        client.execute { create index `inu-percolate` }
      else
        Future { s"inu-percolate" -> resp } }
  }

  lazy val `PUT inu-percolate/_mapping/.percolator` = {
    import PercolatorIndex._
    import PercolatorIndex.fields._
    import context.dispatcher
    client.execute {
      put mapping `inu-percolate/.percolator` as (referredClauses, tags, enabled, keywords, title) ignoreConflicts true
    }.map { s"PUT ${`inu-percolate`}/_mapping/.percolator" -> _ }
  }

  def putInuPercolateMapping(tpe: String, indexAnalyzer: String): Future[(String, AcknowledgedResponse)] = {
    import PercolatorIndex._
    import PercolatorIndex.fields._
    import context.dispatcher
    client.execute {
      put mapping IndexType(`inu-percolate`, tpe) as `dialogs, agent*, customer*`(indexAnalyzer) ignoreConflicts true
    }.map { s"PUT ${`inu-percolate`}/_mapping/$tpe" -> _ }
  }

   def `GET inu-percolate/.percolator/_search`(blocks: List[QueryDefinition]): Future[SearchResponse] = {
    
     import PercolatorIndex._
     val request = search in `inu-percolate/.percolator` query bool { must { blocks } } fields ("title", "tags") size 50 postFilter not(idsFilter("temporary"))
    client.execute { request }
  }

  def client = elastics.Cluster.`4s client`
}

object AnalyzersIndex {
  val `analyzers` = "analyzers"

  object fields  {
    val dictionary = "dictionary" typed StringType index "not_analyzed"
    val stopwords = "stopwords" typed StringType index "not_analyzed"
  }

  lazy val ik = {
    import fields._
    mapping("ik") as Seq(dictionary, stopwords) all false
  }

  val `analyzers/ik` = IndexType(`analyzers`, ik.`type`)
}

trait AnalyzersIndex extends util.ImplicitActorLogging {
  self: Actor =>

  import AnalyzersIndex._
  import context.dispatcher

  lazy val `PUT analyzers` = {

    elastics.Cluster.`4s client`.execute { index exists `analyzers` }.flatMap { resp =>
      if (resp.isExists)
       Future { (s"analyzers" -> resp) }
      else
        elastics.Cluster.`4s client`.execute { create index `analyzers` mappings(ik) }.map { s"PUT ${`analyzers`}" -> _ }
    }
  }
}