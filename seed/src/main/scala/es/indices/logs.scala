package es.indices

import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.client.Client
import org.elasticsearch.common.text.Text
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.aggregations.AggregationBuilders
import elastic.ImplicitConversions._
import org.elasticsearch.search.aggregations.bucket.filters.FiltersAggregationBuilder
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContextExecutor, Future, ExecutionContext}
import scala.util.Try

object logs {

  implicit class SearchRequestBuilder0(builder: SearchRequestBuilder) {
    def setVttHighlighter = {
      builder.addField(VttField.NAME)
        .setHighlighterRequireFieldMatch(true)
        .setHighlighterNumOfFragments(0)
        .setHighlighterPreTags("<em>")
        .setHighlighterPostTags("</em>")
        .addHighlightedField("agent*")
        .addHighlightedField("customer*")
        .addHighlightedField("dialogs")
    }
  }

  implicit class String0(value: Option[String]) {
    def asTypeQuery = {
      value match {
        case None => boolQuery()
        case Some(value) =>
          boolQuery().filter(value.split("""(\s+|,)""").foldLeft(boolQuery()){ (acc, t) => acc.should(QueryBuilders.typeQuery(t))})
      }
    }
  }

  def buildSourceAgg(key: String = "source")(implicit client: Client, executionContext: ExecutionContext): Future[FiltersAggregationBuilder] = {
    getTemplate.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get)
      .map { resp =>
        resp.getMappings.foldLeft(AggregationBuilders.filters(key)) { (acc, m) =>
          acc.filter(m.key, QueryBuilders.typeQuery(m.key))
        }
      }
  }

  def putIndexTemplate(implicit client: Client) =
    client.admin().indices().preparePutTemplate("template1")
      .setSource(
        s"""{
          | "template" : "logs-*",
          | "mappings" : { ${template1("ytx")}, ${template1("ami-l8k")} }
          |}""".stripMargin).execute()

  def template1(source: String) = s"""
    |"$source": {
    |        "_source": {
    |          "enabled": true
    |        },
    |        "dynamic": "dynamic",
    |        "dynamic_templates": [
    |          {
    |            "agent": {
    |              "mapping": {
    |                "analyzer": "whitespace_stt_analyzer",
    |                "type": "string"
    |              },
    |              "match_mapping_type": "string",
    |              "match": "agent*"
    |            }
    |          },
    |          {
    |            "customer": {
    |              "mapping": {
    |                "analyzer": "whitespace_stt_analyzer",
    |                "type": "string"
    |              },
    |              "match_mapping_type": "string",
    |              "match": "customer*"
    |            }
    |          },
    |          {
    |            "vtt": {
    |              "mapping": {
    |                "type": "string"
    |              },
    |              "match_mapping_type": "string",
    |              "match": "vtt"
    |            }
    |          },
    |          {
    |            "dialogs": {
    |              "mapping": {
    |                "analyzer": "whitespace_stt_analyzer",
    |                "type": "string"
    |              },
    |              "match_mapping_type": "string",
    |              "match": "dialogs"
    |            }
    |          }
    |        ],
    |        "dynamic_date_formats" : ["YYYY-MM-dd hh:mm:ss"],
    |        "_all": {
    |          "enabled": false
    |        },
    |        "properties": {
    |          "path": {
    |            "index": "not_analyzed",
    |            "type": "string"
    |          },
    |          "parties": {
    |            "type": "string"
    |          },
    |          "agentTeamName": {
    |            "type": "string",
    |            "index": "not_analyzed"
    |          },
    |          "custGrade": { "type": "string", "index": "not_analyzed" },
    |          "gameType": { "type": "string", "index": "not_analyzed" },
    |          "recordRang": { "type": "long" },
    |          "recordTime": { "type": "date" }
    |        },
    |        "_meta" : {
    |           "properties" : {
    |             "agentTeamName" : {
    |               "queries" : {
    |                             "term"  : { "value" : "rd1" },
    |                             "terms" : { "value" : [ "rd1", "rd2" ]}
    |                           }
    |             },
    |             "recordTime" : {
    |               "queries" : {
    |                             "range" : { "gte" : "2015-05-29T07:01:51+08:00", "lte" : "2015-12-05T07:01:51+08:00" },
    |                             "term"  : { "value" : "2015-05-29T07:01:51+08:00" },
    |                             "terms" : { "value" : [ "2015-05-29T07:01:51+08:00", "2015-05-30T07:01:51+08:00"] }
    |                           }
    |             },
    |             "custGrade" : {
    |               "queries" : { "term" : { "value" : 1 }, "terms" : { "value" : [ 1, 2 ] } }
    |             }
    |           }
    |        }
    |      }
  """.stripMargin

  def getTemplate(implicit client: Client): Future[GetIndexTemplatesResponse] =
    client.admin()
      .indices()
      .prepareGetTemplates("template1")
      .execute().future


  def getProperties(`type`: String)(implicit client: Client, ctx: ExecutionContextExecutor): Future[List[String]] = {
    for {
      templates       <- logs.getTemplate
      template1       <- templates.getIndexTemplates.headOption.future(new Exception("template1 doesn't exist"))
      json            <- template1.mappings.find { _.key == `type` }.flatMap { x => (parse(x.value.string()) \ `type`).toOption }.future()
      JObject(props0) <- (json \ "_meta" \ "properties").toOption.future()
      JObject(props1) <- (json \ "properties").toOption.future()
    } yield props0.map(_._1).intersect(props1.map(_._1))
  }

  def prepareGet(r: GetRequest)(implicit client: Client) =
    client.prepareGet(r.index(), r.`type`(), r.id())
      .setFields("vtt")
      .execute()


  def prepareSearch(indices: String = "logs-*")(implicit client: Client) =
  client.prepareSearch(indices)

  def prepareCount(indices: String = "logs-*")(implicit client: Client) =
    client.prepareCount(indices)

  case class VttHighlightFragment(start: String, keywords: String)

  object VttField {

    import org.elasticsearch.action.get.GetResponse

    import scala.collection.JavaConversions._

    val NAME = "vtt"

    private val line = """(.+-\d+)\s([\s\S]+)\s$""".r

    implicit class fieldValuesToMap(values: java.util.List[Object]) {
      def asMap() = values.foldLeft(Map.empty[String, String]) { (acc, v) => {
        val line(cueid, content) = s"$v"
        acc + (cueid -> content)
      }
      }
    }

    def unapply(value: AnyRef): Option[Map[String, String]] = {
      value match {

        case doc: GetResponse if doc.exists {_.getName equals NAME } =>
          Some(doc.getField(NAME).getValues.asMap())

        case h: SearchHit if h.fields().containsKey(NAME) =>
          Some(h.field(NAME).getValues.asMap())

        case unexpected =>
          println(s"unexpected unapply VttField$unexpected")
          None
      }
    }
  }



  object SearchHitHighlightFields {

    object Path {
      def unapply(h: SearchHit): Option[String] = Some(s"${h.index}/${h.`type`}/${h.id}")
    }

    val insideHighlightTag = """(?:<\w+\b[^>]*>)([^<>]*)(?:<\/\w+>)""".r
    // "agent0-780</c> 喂哎哎您好下<c>女士</c>是吧" extract '780' and '喂哎哎您好下<c>女士</c>是吧'
    val highlightedSentence = """(?:<\w+>)*((?:agent|customer)\d+-\d+)(?:<\/(?:em|c)>)*\s([\s\S]+)""".r
    val highlightFragment = """(?:[^\n]*[<>]+[^\n]*)""".r
    val insideTagV = """(<v\b[^>]*>)[^<>]*(<\/v>)"""
    val startTime = """^(\d{2,3}[:\.]?)+""".r
    val party = """\w+(?=-)""".r

    def splitFragment(fragment: Text): List[String] = {
      //import util.ImplicitPrint._
      //println(s"$fragment")
      (highlightFragment findAllIn fragment.string()).toList//.println()
    }

    def substitute(vtt: Map[String, String])(txt: String): Try[VttHighlightFragment] =
      Try(txt match {
        case highlightedSentence(cueid, highlighted) =>
          //println(s"$cueid, $highlighted")
          for {
            highlightedSubtitle <- Try(vtt(cueid).replaceAll(insideTagV, s"$$1$highlighted$$2"))
            keywords            <- Try((for (m <- insideHighlightTag findAllMatchIn highlighted) yield m group 1).mkString(" "))
            time                <- Try(startTime.findFirstIn(highlightedSubtitle).get)
          } yield VttHighlightFragment(time, keywords)
      }).flatten

    def unapply(value: AnyRef): Option[(String, scala.Iterable[VttHighlightFragment])]= {
      value match {
        case h: SearchHit =>
          val VttField(map) = h
          val Path(path) = h

          Some((path, h.highlightFields.values.flatMap(_.fragments().flatMap(splitFragment)).flatMap(substitute(map)(_).toOption)))
        case _ => None
      }
    }
  }

}
