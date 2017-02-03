package com.inu.cluster.storedquery.algorithm

import scala.util.matching.Regex

/**
  * Created by henry on 1/4/17.
  */
object ListOfListCombination {

  implicit class ListOfListArg(x: List[List[String]]) {
    def gen: List[List[String]] = x match {
      case Nil    => List(Nil)
      case h :: _ => h.flatMap(i => x.tail.gen.map(i :: _))
    }
  }

  // sample: a1/a2, b1/b2
  // output [ a1 b1 ] [ a1 b2 ] [ a2 b1 ] [ a2 b2 ]
  def divideBySlash(q: String): List[List[String]] = q.split("""[\s,]+""").map(_.split("\\/").toList).toList

  val SlashRegex: Regex = """\/""".r
}
