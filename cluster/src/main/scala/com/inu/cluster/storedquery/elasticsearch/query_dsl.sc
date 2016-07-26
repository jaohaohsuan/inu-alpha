import com.inu.cluster.storedquery.elasticsearch.MultiSpanNearQuery
import com.inu.protocol.storedquery.messages.SpanNearClause
import org.json4s._

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