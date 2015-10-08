package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
object Terminology {

  val Occurrences = Seq("must", "must_not", "should")
  val BoolQueryClauses = Seq("match", "near", "named")

  val OccurrenceRegex = """^must$|^must_not$|^should$""".r
  val BoolQueryClauseRegex = """^match$|^near$|^named$""".r

}
