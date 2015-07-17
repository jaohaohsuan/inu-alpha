package util

import com.sksamuel.elastic4s.{BoolQueryDefinition, MultiMatchQueryDefinition, ElasticsearchClientUri, ElasticClient, WhitespaceAnalyzer}
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
