import com.inu.cluster.storedquery.elasticsearch.SynonymBoolQuery
import com.inu.protocol.storedquery.messages.MatchClause
import org.json4s._
import org.json4s.native.JsonMethods._

val ll = List[List[String]](List("1", "2"))

val x :: xs = ll

def generator(x: List[List[String]]): List[List[String]] = x match {
  case Nil    => List(Nil)
  case h :: _ => h.flatMap(i => generator(x.tail).map(i :: _))
}

val iterators = generator(List(List("临时","暂时"), List("额度", "信用", "金额")))

iterators.foreach(Console.println)
