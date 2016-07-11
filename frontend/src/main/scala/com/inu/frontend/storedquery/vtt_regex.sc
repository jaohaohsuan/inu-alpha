import com.inu.frontend.elasticsearch.SearchHitHighlightFields
import org.elasticsearch.common.text.Text

SearchHitHighlightFields.splitFragment(
new Text("agent0-1190 老家 超 同学 的 家长 对 吗"))
// 没tag需为空

SearchHitHighlightFields.splitFragment(
new Text("agent0-1190 <c>家长<c> 对 吗"))
// 有任何tag就会输出

def toSeq(p: Option[String]): Seq[String] = ("""\w+""".r findAllIn p.getOrElse("")).toSeq
def toList(p: Option[String]): List[String] = p.getOrElse("").split("""\s+""").toList
toSeq(Some(""))
toSeq(None)
toSeq(Some(" 234 4,95 3 3 30:30   ee+ee    3 4.9")).toList

toList(Some(" 234 495 3 3 3030     3 49"))
