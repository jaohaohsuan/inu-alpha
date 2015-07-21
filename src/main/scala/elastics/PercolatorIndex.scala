package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.DynamicMapping.Dynamic
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s._
import org.elasticsearch.action.search.SearchResponse

import scala.concurrent.Future

object PercolatorIndex {

  val `inu-percolate` = "inu-percolate"

  object fields {

    val defaultAnalyzer = WhitespaceAnalyzer

    val title = "title" typed StringType index "not_analyzed"
    val referredClauses = "referredClauses" typed StringType analyzer defaultAnalyzer
    val tags = "tags" typed StringType analyzer defaultAnalyzer nullValue "" includeInAll false
    val enabled = "enabled" typed BooleanType index "not_analyzed" includeInAll false
    val keywords = "keywords" typed StringType analyzer defaultAnalyzer nullValue ""
    
    lazy val `dialogs, agent*, customer*` = {
      import LteTemplate.fields._
      (0 to 5).foldLeft(List(dialogs)){ (acc, n) =>
        (s"customer$n" typed StringType analyzer defaultAnalyzer) :: (s"agent$n" typed StringType analyzer defaultAnalyzer) :: acc
      }
    }
  }

  import fields._
  lazy val `.percolator` = ".percolator" as (referredClauses, tags, enabled, keywords, title)

  lazy val logs = {
    import fields._
    mapping("logs") as `dialogs, agent*, customer*`
  }

  val `inu-percolate/.percolator` = IndexType(`inu-percolate`, `.percolator`.`type`)
  val `inu-percolate/logs` = IndexType(`inu-percolate`, logs.`type`)
}

trait PercolatorIndex extends util.ImplicitActorLogging{
  self: Actor ⇒


  lazy val `PUT inu-percolate` = {
    import PercolatorIndex._
    import PercolatorIndex.fields._
    import context.dispatcher

    client.execute { index exists `inu-percolate` }.flatMap { resp =>
      if (resp.isExists)
        client.execute {
          put mapping `inu-percolate/logs` as `dialogs, agent*, customer*` ignoreConflicts true }
          .map { s"PUT ${`inu-percolate`}/_mapping/${logs.`type`}" -> _ }
      else
        client.execute { create index `inu-percolate` mappings(logs, `.percolator`) }
          .map { s"PUT ${`inu-percolate`}" -> _ }
    }
  }

   def `GET inu-percolate/.percolator/_search`(blocks: List[QueryDefinition]): Future[SearchResponse] = {
    
     import PercolatorIndex._
     val request = search in `inu-percolate/.percolator` query bool { must { blocks } } fields ("title", "tags") size 50
    client.execute { request }
  }

  def client: ElasticClient

}