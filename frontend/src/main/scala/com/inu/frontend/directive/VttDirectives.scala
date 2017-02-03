package com.inu.frontend.directive

import com.inu.frontend.elasticsearch.SearchHitHighlightFields
import com.inu.frontend.elasticsearch.SearchHitHighlightFields._

import scala.collection.JavaConversions._
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.percolate.PercolateResponse.Match
import org.elasticsearch.index.get.GetField
import spray.routing._

import scala.util.{Success, Try}
import scala.util.matching.Regex

trait VttDirectives extends Directives {

  type VttSubtitles = Map[String, String]

  def format(gf: GetField): Directive1[VttSubtitles] = {
    /*
    input sample:

    customer0-1394 00:00:01.394 --> 00:00:01.506 <v R0>是 </v>

     */
    val split: Regex = """(.+-\d+)\s([\s\S]+)\s$""".r
    val subs = gf.getValues.foldLeft(Map.empty[String, String]){ (acc, s) =>
      s.toString match {
        case split(cueid, content) =>
          //cueid:   customer0-1394
          //content: 00:00:01.394 --> 00:00:01.506 <v R0>是 </v>
          acc + (cueid -> content)
        case _ => acc
      }
    }
    provide(subs)
  }

  def extractFragments(matches: Iterable[Match]): Directive1[VttSubtitles] = {
    import SearchHitHighlightFields._
     provide(matches.flatMap { m =>
       Try(m.getHighlightFields.toMap).getOrElse(Map.empty)
      }
      .flatMap { case (_, hf) => hf.fragments().flatMap(splitFragment) }
      .flatMap {
        case highlightedSentence(cueid, highlight) => Some(cueid -> highlight)
        case _ => None
      }.toMap)
  }

  implicit class VttOps(origin: VttSubtitles) {

    private def substitute(sub: (String, String)): (String, String) = {
      val (cueid, highlight) = sub
      (for {
          subtitle <- origin.get(cueid)
          tag <- party.findFirstIn(cueid).map { txt => s"c.$txt" }
        } yield cueid -> subtitle.replaceAll( insideTagV, s"$$1$highlight$$2").replaceAll("""(?<=\<)c(?=>)""", tag))
        .getOrElse(sub)
    }

    def highlightWith(segments: VttSubtitles): VttSubtitles =  origin ++ segments.map(substitute)
  }

}
