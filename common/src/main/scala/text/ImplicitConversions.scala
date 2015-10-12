package text

import scala.language.implicitConversions

/**
 * Created by henry on 10/12/15.
 */
object ImplicitConversions {

  import scala.language.reflectiveCalls
  implicit def RichFormatter(string: String) = new {
    def richFormat(replacement: Map[String, Any]) =
      (string /: replacement) {(res, entry) => res.replaceAll("#\\{%s\\}".format(entry._1), entry._2.toString)}
  }
}
