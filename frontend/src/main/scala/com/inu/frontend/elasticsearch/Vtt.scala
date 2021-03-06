package com.inu.frontend.elasticsearch

import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.common.text.Text
import org.elasticsearch.search.SearchHit
import org.joda.time.IllegalFieldValueException

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

case class VttHighlightFragment(start: String, keywords: String)

object VttField {

    import org.elasticsearch.action.get.GetResponse

    import scala.collection.JavaConversions._

    val NAME = "vtt"

    private val line = """(.+-\d+)\s([\s\S]+)\s$""".r

    implicit class fieldValuesToMap(values: java.util.List[Object]) {
      def asMap() = values.foldLeft(Map.empty[String, String]) { (acc, v) =>
        val line(cueid, content) = s"$v"
        acc + (cueid -> content)
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

object SearchHitHighlightFields extends LazyLogging {

    object Path {
      def unapply(h: SearchHit): Option[String] = Some(s"${h.index}/${h.`type`}/${h.id}")
    }

    val insideHighlightTag = """(?:<\w+\b[^>]*>)([^<>]*)(?:<\/\w+>)""".r
    // "agent0-780</c> 喂哎哎您好下<c>女士</c>是吧" extract '780' and '喂哎哎您好下<c>女士</c>是吧'
    val highlightedSentence = """(?:<\w+>)*((?:agent|customer)\d+-\d+)(?:<\/(?:em|c)>)*\s([\s\S]+)""".r

    // agent0-1190 您好 请问是 姓名 小学 三 一般 老家 超 同学 的 <em>家长</em> 对 吗
    // 匹配有<anyTag></anyTag>
    val highlightFragment = """(?:[^\n]*[<>]+[^\n]*)""".r

    val insideTagV = """(<v\b[^>]*>)[^<>]*(<\/v>)"""
    val startTime = """^(\d{2,3}[:\.]?)+""".r
    val party = """\w+(?=-)""".r

    def splitFragment(fragment: Text): List[String] = {
      // sample: agent0-1190 您好 请问是 姓名 小学 三 一般 老家 超 同学 的 <em>家长</em> 对 吗
      // 匹配有<em></em>, 如果没有匹配返回空List
      (highlightFragment findAllIn fragment.string()).toList
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

  def intStartTime(path: String)(value: VttHighlightFragment): Int = {

    Try(org.joda.time.format.DateTimeFormat.forPattern("HH:mm:ss.SSS").parseDateTime(value.start).getMillisOfDay) match {
      case Success(v) => v
      case Failure(ex: IllegalFieldValueException) =>
        val `HH:>60:ss.SSS` = """(\d+):(\d+):(.*)""".r
        value.start match {
          case `HH:>60:ss.SSS`(hour, min, sec) => intStartTime(path)(value.copy(start = s"$hour:${min.toInt % 60}:$sec"))
          case _ =>
            logger.error(s"Vtt highlighting fail while parsing vtt start time '${value.start} from source path '$path'", ex)
            Int.MaxValue
        }

      case Failure(ex) =>
        logger.error(s"Vtt highlighting fail while parsing vtt start time '${value.start} from source path '$path'", ex)
        Int.MaxValue
    }
  }

  def unapply(value: AnyRef): Option[(String, List[VttHighlightFragment])]= {
      value match {
        case h: SearchHit =>
          val VttField(map) = h
          val Path(path) = h

          val startTime = intStartTime(path)(_)
          Some(path, h.highlightFields.values
            .flatMap(_.fragments().flatMap(splitFragment))
            .flatMap(substitute(map)(_).toOption).toList.sortBy(startTime))
        case _ => None
      }
    }
  }

