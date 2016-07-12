import com.inu.cluster.storedquery.elasticsearch.MultiSpanNearQuery
import com.inu.protocol.storedquery.messages.SpanNearClause

val spn1 = SpanNearClause("h w", "agent*", 3, false, "must")

val MultiSpanNearQuery(json) = spn1

import org.json4s.native.JsonMethods._

pretty(render(json))