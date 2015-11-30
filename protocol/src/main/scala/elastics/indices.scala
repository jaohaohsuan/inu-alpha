package protocol.elastics

/**
 * Created by henry on 9/6/15.
 */
object indices {
  val percolator = "inu-percolate"
}

object boolQuery {

  val Occurrences = Seq("must", "must_not", "should")

  val OccurrenceRegex = """^must$|^must_not$|^should$""".r

}