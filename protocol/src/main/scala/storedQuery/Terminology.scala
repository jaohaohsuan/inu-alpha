package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
object Terminology {

  val BoolQueryClauses = Seq("match", "near", "named")
  val BoolQueryClauseRegex = """^match$|^near$|^named$""".r

}
