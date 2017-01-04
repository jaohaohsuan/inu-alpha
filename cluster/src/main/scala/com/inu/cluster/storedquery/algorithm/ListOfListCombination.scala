package com.inu.cluster.storedquery.algorithm

/**
  * Created by henry on 1/4/17.
  */
object ListOfListCombination {

  def generator(x: List[List[String]]): List[List[String]] = x match {
    case Nil    => List(Nil)
    case h :: _ => h.flatMap(i => generator(x.tail).map(i :: _))
  }

}
