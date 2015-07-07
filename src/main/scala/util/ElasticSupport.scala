package util

import com.sksamuel.elastic4s.{ElasticsearchClientUri, ElasticClient}
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress

object ElasticSupport {

  private val settings = ImmutableSettings.settingsBuilder()
    .put("action.auto_create_index", false)
    .put("es.logger.level", "INFO")


  val client = ElasticClient.local(settings.build())

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
          "tags" typed StringType index "not_analyzed" includeInAll false nullValue("")
          )
        ,
        "stt" as Seq (
          "dialogs" inner (
            "name" typed StringType index "not_analyzed",
            "content" typed StringType,
            "time" typed IntegerType index "not_analyzed"
            )
        ))
    }
  }
}
