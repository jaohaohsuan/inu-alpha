package es.indices

import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.SearchHit
import org.elasticsearch.common.text.Text
import org.elasticsearch.search.aggregations.AggregationBuilders
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

import scala.util.{Failure, Success, Try}

object logs {

  def buildSourceAgg(key: String = "source")(implicit client: Client, executionContext: ExecutionContext) = {
    import elastic.ImplicitConversions._
    getTemplate.asFuture.map(_.getIndexTemplates.headOption).filter(_.isDefined).map(_.get)
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
    |          "vtt": {
    |           "analyzer": "whitespace",
    |            "type": "string"
    |          },
    |          "parties": {
    |            "type": "string"
    |          },
    |          "dialogs": {
    |           "analyzer": "whitespace_stt_analyzer",
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
    |        }
    |      }
  """.stripMargin

  def getTemplate(implicit client: Client) =
    client.admin()
      .indices()
      .prepareGetTemplates("template1")
      .execute()

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

    def substitute(vtt: Map[String, String])(txt: String): Option[VttHighlightFragment] = {
      txt match {
        case highlightedSentence(cueid, highlighted) =>
          //println(s"$cueid, $highlighted")
          (for {
            highlightedSubtitle <- Try(vtt(cueid).replaceAll(insideTagV, s"$$1$highlighted$$2"))
            keywords <- Try((for (m <- insideHighlightTag findAllMatchIn highlighted) yield m group 1).mkString(" "))
            time <- Try(startTime.findFirstIn(highlightedSubtitle).get)
          } yield VttHighlightFragment(time, keywords)) match {
            case Failure(ex) =>
              println(s"extract error: $highlighted ${ex}")
              None
            case Success(v) => Some(v)
          }

        case _ =>
          println(s"highlightedSentence '$txt' unmatched.")
          None
      }
    }

    def unapply(value: AnyRef): Option[(String, scala.Iterable[VttHighlightFragment])]= {
      value match {
        case h: SearchHit =>
          val VttField(map) = h
          Some((s"${h.index}/${h.`type`}/${h.id}",
            h.highlightFields.values.flatMap(_.fragments().flatMap(splitFragment)).flatMap(substitute(map)(_))))
        case _ => None
      }
    }
  }

}
