package river.ami

import org.json4s.JsonAST.{JArray, JField, JValue}
import org.json4s._
import text.ImplicitConversions._
import scala.collection.JavaConversions._
import scala.collection.immutable.Seq
import scala.xml.{Elem, Node, NodeSeq}


case class SentencePeriod(str: String) {
  require(str.nonEmpty, "period value is empty")
  require(validation(values), s"invalid Time values($values)")

  lazy val values = str.split(" ").map(_.split(",").map(_.trim).filter(_.matches("""\d*""")).map(_.toInt).head).toList

  def validation(values: List[Int]) = {
     values match {
       case (begin: Int) :: Nil => true
       case (begin: Int) :: xs => begin < xs.last
       case _ => false
    }
  }
}

case class ItemElem(node: Node) {
  require(node.label == "Item", s"${node.label} doesn't support excepted 'Item'")
  require(text.nonEmpty, "silence found")

  private val period = SentencePeriod((node \ "Time").text.trim)

  lazy val text = (node \ "Text").text

  lazy val begin :: _ =  period.values
  lazy val end :: _ = period.values.reverse

}
case class RoleElem(elem: Elem) {

  val Role = """[R,r]\d+""".r

  require(elem.label == "Role", s"${elem.label} doesn't support excepted 'Role'")
  require(Role.matches(name), s"""Attributes ${elem.attributes} doesn't match any rule that is Name="Role{number}"""")
  //require(items.nonEmpty , s"'$elem' Role must contains Items")

  lazy val name = elem.attributes.get("Name").map(_.text.trim).getOrElse("")

  lazy val items: Seq[ItemElem] = (elem \\ "Item").map(ItemElem)

}

case class VttSupportedLogs(node: Node) {

  //val roles = nodeSeq.iterator.filter(_.isInstanceOf[Elem]).map(_.asInstanceOf[Elem]).map(RoleElem)

}

case class XmlStt(agentPartyCount:    Int = 0,
                  customerPartyCount: Int = 0,
                  parties:         Map[String,String] = Map.empty,
                  body: JObject = JObject(), mixed: List[VttSentence] = List.empty) {

  val agentRole = """[R,r]0""".r
  val customerRole = """[R,r]1""".r

  import org.json4s.JsonDSL._

  lazy val asResult: XmlStt = {

    val (dialogsArr, vttArr) = mixed.sortBy { s => s.end }.foldLeft((List.empty[JString],List.empty[JString])){ (acc, s) =>
      val (dialogs, vtt) = acc
      (dialogs.:+(JString(s.text)), vtt.:+(JString(s.subtitle)))
    }

    copy(body = body ~
      ("vtt" -> JArray(vttArr)) ~
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

  private def defineProperties(code: String): (XmlStt, VttSentence)= {
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

    (doc, VttSentence(code, doc.parties(code)))
  }

  def append(n: Elem) = {

    val re = RoleElem(n)
    val (transformed, sentence) = defineProperties((n \ "@Name").text)

    re.items.foldLeft(transformed){ (acc, item) =>

      val begin = item.begin
      val end = item.end
      val content = item.text//.replaceAll("""\s+""", "")

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
