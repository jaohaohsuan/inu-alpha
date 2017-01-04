import com.inu.cluster.storedquery.elasticsearch.{MultiSpanNearQuery, SynonymBoolQuery}
import com.inu.protocol.storedquery.messages.{MatchClause, SpanNearClause}
import org.json4s._

"""[^\w]+""".r.findFirstIn("3838").nonEmpty

val spn1 = SpanNearClause("h w", "agent*", 3, false, "must")

val MultiSpanNearQuery(json) = spn1

import org.json4s.native.JsonMethods._

pretty(render(json))

("""[\w\u4e00-\u9fa5]+""".r findAllIn "高興 為您 hello world").toList

"""[\w\*]+""".r findAllIn "agent*"
"agent*,, customer*".split("""[\s,]+""").toList

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.{write}

val json2 = JObject(List(JField("id", JArray(List(JString("hello"), JNothing)))))

pretty(render(json2))

"  ,  ".split("""[\s,]+""").toList

"cc/g/gg a/b".split("""[\s,]+""").flatMap{_.split("\\/")}

"""\/""".r.findFirstMatchIn("a/bc/cc a")


MatchClause("临时/暂时 额度/信用/金额", "agent*","or", "must") match {
  case SynonymBoolQuery(json) => println(compact(render(json)))
  case _ => println("opps")
}

