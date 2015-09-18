package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
object Terminology {

  val OccurrenceRegex = """^must$|^must_not$|^should$""".r
  val BoolQueryClauseRegex = """^match$|^near$|^named$""".r

}
