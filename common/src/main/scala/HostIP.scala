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
