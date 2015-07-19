package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.DynamicMapping.Dynamic
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings.TypedFieldDefinition
import com.sksamuel.elastic4s.{QueryDefinition, IndexType, ElasticClient, WhitespaceAnalyzer}
import org.elasticsearch.cluster.metadata.MappingMetaData

import scala.concurrent.Future

object PercolatorIndex {

  val `inu-percolate` = "inu-percolate"

  object fields {
    val referredClauses = "referredClauses" typed StringType analyzer WhitespaceAnalyzer includeInAll true
    val tags = "tags" typed StringType analyzer WhitespaceAnalyzer includeInAll false nullValue ""
    val enabled = "enabled" typed BooleanType index "not_analyzed" includeInAll false
    
    lazy val `dialogs, agent*, customer*` = {
      import LteTemplate.fields._
      (0 to 9).foldLeft(List(dialogs)){ (acc, n) =>
        (s"customer$n" typed StringType) :: (s"agent$n" typed StringType) :: acc
      }
    }
  }

  import fields._
  lazy val `.percolator` = ".percolator" as (
    "query" typed ObjectType enabled true analyzer WhitespaceAnalyzer,
    referredClauses,
    tags,
    enabled
    ) dynamic Dynamic

  lazy val sample = {
    import fields._
    mapping("sample") as `dialogs, agent*, customer*`
  }

  val `inu-percolate/.percolator` = IndexType(`inu-percolate`, `.percolator`.`type`)
  val `inu-percolate/sample` = IndexType(`inu-percolate`, sample.`type`)

}

trait PercolatorIndex extends util.ImplicitActorLogging {
  self: Actor ⇒

  lazy val `PUT inu-percolate` = {
    import PercolatorIndex._
    import PercolatorIndex.fields._
    import PercolatorIndex.`inu-percolate`
    import context.dispatcher

    client.execute { index exists `inu-percolate` }.flatMap { resp =>
      if (resp.isExists)
        client.execute {
          put mapping `inu-percolate/sample` as `dialogs, agent*, customer*` ignoreConflicts true }
          .map { "PUT inu-percolate/_mapping/sample" -> _ }
      else
        client.execute { create index `inu-percolate` mappings(sample, `.percolator`) }.map { "PUT inu-percolate" -> _ }
    }
  }

  def client: ElasticClient

}