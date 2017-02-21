val es5 =
  """
    |{
    |   "took": 61,
    |   "hits": {
    |      "hits": [
    |         {
    |            "_index": "stored-query5",
    |            "_type": "queries",
    |            "_id": "AVpFByQT3Ad45ybuFrOs",
    |            "_source": {
    |               "query": {
    |                  "term": {
    |                     "dialogs": "信用卡"
    |                  }
    |               }
    |            },
    |            "highlight": {
    |               "dialogs": [
    |                  "customer0-9356 是因為 之前 我是 那 個 <em>信用卡</em> 有 換卡 的 關繫 ",
    |                  "customer0-92680 因為 我 今天是 要 <em>信用卡</em> 的 那 個 扣款 他也 是 沒有 幫我 扣 嗎 "
    |               ],
    |               "agent0": [
    |                  "agent0-143004 那 今年 一月 份 應該 要 從您 約定 的 <em>信用卡</em> 進行 扣款 "
    |              ]
    |            }
    |         }
    |      ]
    |   }
    |}
  """.stripMargin

val es2 =
  """
    |{
    |  "took": 49,
    |  "_shards": {
    |    "total": 5,
    |    "successful": 5,
    |    "failed": 0
    |  },
    |  "total": 35,
    |  "matches": [
    |    {
    |      "_index": "stored-query",
    |      "_id": "560263550",
    |      "highlight": {
    |        "dialogs": [
    |          "customer0-300914 如果 我現在 要 <em>解約</em> 的話 ",
    |          "customer0-305498 目前 的話 如果 <em>解約</em> 時 "
    |        ]
    |      }
    |    }
    |  ]
    |}
  """.stripMargin

import com.inu.frontend.elasticsearch.SearchHitHighlightFields
import org.json4s.JsonAST
import org.json4s.JsonAST.{JArray, JField, JString}
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._

def ff(value: JArray): List[String] = value.arr.flatMap {
  case JString(s) => Some(s)
  case _ => None
}

val es5j = parse(es5)
val highlights = es5j filterField {
  case JField("dialogs", _: JArray) => true
  case JField(name, _: JArray) if name.matches("""agent\d""") => true
  case JField(name, _: JArray) if name.matches("""customer\d""") => true
  case _ => false
} flatMap {
  case JField(_, value: JArray) => Some(ff(value))
  case _ => None
} flatten

val matches: Seq[String] = es5j  \\ classOf[JString]
matches.foreach { a =>
//  a match {
//    case SearchHitHighlightFields.highlightedSentence(cuid, content) => println(s"$cuid $content")
//    case _ =>

//  }
  println(a)
}
