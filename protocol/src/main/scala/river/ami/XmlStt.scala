package river.ami


import org.json4s.JsonAST.{JArray, JField, JValue}
import org.json4s._

import scala.xml.NodeSeq

case class XmlStt(agentPartyCount:    Int = 0,
                  customerPartyCount: Int = 0,
                  parties:         Map[String,String] = Map.empty,
                  body: JObject = JObject(), mixed: List[SttSentence] = List.empty) {

  val agentRole = """[R,r]0""".r
  val customerRole = """[R,r]1""".r

  import org.json4s.JsonDSL._

  def asResult() = {

    val (dialogsArr, vttArr) = mixed.sortBy { s => s.end }.foldLeft((List.empty[JString],List.empty[JString])){ (acc, s) =>
      val (dialogs, vtt) = acc
      (dialogs.:+(JString(s.text)), vtt.:+(JString(s.subtitle)))
    }

    copy(body = body ~
      ("river" -> JArray(vttArr)) ~
      ("dialogs" -> JArray(dialogsArr)) ~
      ("parties" -> JArray(parties.keys.map{s => JString(s)}.toList ))
    )
  }

  private def addField(field: String, default: JValue = JArray(List.empty)): XmlStt = {
    body \ field match {
      case JNothing => copy(body = body ~ (field -> default))
      case _ => this
    }
  }

  private def defineProperties(code: String): (XmlStt, SttSentence)= {
    import text.ImplicitConversions._

    val doc = code match {
      case r if agentRole.matches(r) && !parties.contains(r) =>
        val alias = s"agent$agentPartyCount"
        copy(parties = parties + (r -> alias), agentPartyCount = agentPartyCount + 1).addField(alias)

      case r if customerRole.matches(r) && !parties.contains(r) =>
        val alias = s"customer$customerPartyCount"
        copy(parties = parties + (r -> alias), customerPartyCount = customerPartyCount + 1).addField(alias)

      case _ => this
    }

    (doc, SttSentence(code, doc.parties(code)))
  }

  def append(n: NodeSeq) = {

    val (transformed, sentence) = defineProperties((n \ "@Name").text)

    (n \ "EndPoint" \ "Item").foldLeft(transformed){ (acc, item) =>

      val begin = (item \ "@Begin").text.toInt
      val end = (item \ "@End").text.toInt
      val content = (item \ "Text").text

      acc.body transformField {
        case JField(name, JArray(arr)) if name == sentence.party =>
          sentence.party -> JArray(arr :+ JString(s"${sentence.party}-$begin $content\n"))
      } match {
        case o: JObject => acc.copy(body = o, mixed = acc.mixed.:+(sentence.copy(begin = begin, end = end, content = content)))
        case _ => acc
      }
    }
  }
}
