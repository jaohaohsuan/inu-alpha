package util

import com.sksamuel.elastic4s.{BoolQueryDefinition, ElasticsearchClientUri, ElasticClient}
import domain.StoredQueryAggregateRoot.{NamedBoolClause, SpanNearBoolClause, MatchBoolClause, BoolClause}
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.RemoteTransportException

import scala.concurrent.Future
import scala.reflect.ClassTag

trait ElasticSupport {

  def client: com.sksamuel.elastic4s.ElasticClient

  private val settings = ImmutableSettings.settingsBuilder()
    .put("action.auto_create_index", "-inu*,+*")
    .put("es.logger.level", "INFO")
    .put("node.name", "san")



  //val client = ElasticClient.local(settings.build())

  val percolatorIndex = "inu-percolate"

  def createPercolatorIndex = {

    import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s.mappings.FieldType._

    client.execute {
      create index percolatorIndex mappings (
        mapping name ".percolator" templates (
          template name "template_1" matching "query" matchMappingType "string" mapping {
            field typed StringType
          }
          ),
        ".percolator" as (
          "query" typed ObjectType enabled true,
          "enabled" typed BooleanType index "not_analyzed" includeInAll false,
          "tags" typed StringType index "not_analyzed" includeInAll false nullValue ""
          )
        ,
        "stt" as Seq (
            "dialogs" typed StringType,
            "r0" typed StringType,
            "r1" typed StringType
        ))
    }
  }

  def rge[A<: Throwable: ClassTag](exception: Throwable): Option[A] = {
    exception match {
      case e: RemoteTransportException => rge(e.getCause)
      case e: A => Some(e)
      case _ => None
    }
  }

  def assembleBoolQuery(bool: BoolQueryDefinition,clause: BoolClause): BoolQueryDefinition = {

    import com.sksamuel.elastic4s._

    val qd: QueryDefinition = clause match {
      case MatchBoolClause(query, fields, operator, _) =>
        new MatchQueryDefinition(fields, query).operator(operator.toUpperCase)

      case SpanNearBoolClause(terms, fields, slop, inOrder, _) =>
        val spanNear = new SpanNearQueryDefinition()
        terms.foldLeft(slop.map { spanNear.slop }.getOrElse(spanNear)){ (qb, term) =>
          qb.clause(new SpanTermQueryDefinition(fields, term)) }
          .inOrder(inOrder)
          .collectPayloads(false)

      case NamedBoolClause(_, _, _, clauses) =>
        clauses.values.foldLeft(new BoolQueryDefinition)(assembleBoolQuery)
    }

    clause.occurrence match {
      case "must" => bool.must(qd)
      case "must_not" => bool.not(qd)
      case "should" => bool.should(qd)
    }
  }


  import com.github.nscala_time.time.Imports._

  def getAllIndices(p: (DateTime) => Boolean = (index) => index < DateTime.now) = {

    import scala.concurrent.ExecutionContext.Implicits.global
    import collection.JavaConversions._
    import org.joda.time.format._

    val fmt: DateTimeFormatter = new DateTimeFormatterBuilder()
      .appendLiteral("stt-")
      .append(DateTimeFormat.forPattern("yyyy.MM.dd"))
      .toFormatter

    import com.sksamuel.elastic4s.ElasticDsl._

    client.execute { status("stt*") }.map { _.getIndices.keySet.filter { e => p(fmt.parseDateTime(e)) }.toSeq }
  }

  def filter1() = {

    import com.sksamuel.elastic4s.ElasticDsl._
    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      indicesRanges <- getAllIndices()
      resp <- client.execute { search in(indicesRanges: _*) postFilter {
        rangeFilter("@timestamp") lt "2015.07.08" gt "2015.06.08"
      } }
    } yield resp

  }
}
