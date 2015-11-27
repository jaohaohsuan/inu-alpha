package common


import java.net.{InetAddress}

import scala.util.{Try, Success, Failure}

object HostIP {

  def load(): Option[String] = {

    Try(InetAddress.getLocalHost) match {
      case Success(localhost) => Some(localhost.getHostAddress())
      case Failure(ex) => None
    }
  }
}

/*case class StringSetHolder(set: Set[String]) {
  def append(xs: Set[String]) = StringSetHolder(xs ++ set)
}*/


case class StringMapHolder(map: Map[String, Set[String]]) {
  def append(xs: Map[String, Set[String]]) = copy(map = xs.foldLeft(map)( _ + _ ))

  lazy val text = map.values.flatten.toSet.mkString(" ").trim
}


object Key {
  def gen(map: Map[String, _]): String = {
    def generateNewItemId: String = {
      val id = scala.math.abs(scala.util.Random.nextInt()).toString
      if (map.keys.exists(_ == id)) generateNewItemId else id
    }
    generateNewItemId
  }

  implicit class Map0[A](map: Map[String, A]) {
    def newKey = gen(map)
    /*def add(f: String => A) = {
      val newKey = gen(map)
      map.+(newKey -> f(newKey))
    }*/
  }
}
