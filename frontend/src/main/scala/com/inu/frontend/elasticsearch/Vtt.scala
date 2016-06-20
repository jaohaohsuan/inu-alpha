package com.inu.frontend.elasticsearch

import org.elasticsearch.common.text.Text
import org.elasticsearch.search.SearchHit
import scala.collection.JavaConversions._


import scala.util.Try

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

  def intStartTime(value: VttHighlightFragment): Int = {
    org.joda.time.format.DateTimeFormat.forPattern("HH:mm:ss.SSS").parseDateTime(value.start).getMillisOfDay
  }

  def unapply(value: AnyRef): Option[(String, List[VttHighlightFragment])]= {
      value match {
        case h: SearchHit =>
          val VttField(map) = h
          val Path(path) = h

          Some((path, h.highlightFields.values
                        .flatMap(_.fragments().flatMap(splitFragment))
                        .flatMap(substitute(map)(_).toOption).toList.sortBy(intStartTime)))
        case _ => None
      }
    }
  }

