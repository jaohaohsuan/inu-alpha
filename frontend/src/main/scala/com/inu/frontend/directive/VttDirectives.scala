package com.inu.frontend.directive

import com.inu.frontend.elasticsearch.SearchHitHighlightFields
import scala.collection.JavaConversions._
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.percolate.PercolateResponse.Match
import spray.routing._

import scala.util.matching.Regex

trait VttDirectives extends Directives {

  def getVtt(r: GetResponse) = {
    val split: Regex = """(.+-\d+)\s([\s\S]+)\s$""".r
    val subs = r.getField("vtt").getValues.foldLeft(Map.empty[String, String]){ (acc, s) =>
      s.toString match {
        case split(cueid, content) => acc + (cueid -> content)
        case _ => acc
      }
    }
    provide(subs)
  }

  def highlight(matches: Iterable[Match]) = {
    import SearchHitHighlightFields._

     matches.flatMap(_.getHighlightFields)
      .flatMap { case (_, hf) => hf.fragments().flatMap(splitFragment) }
      //.flatMap(substitute)

  }

}
