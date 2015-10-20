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
}
