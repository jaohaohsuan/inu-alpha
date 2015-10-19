package river.ami

case class VttSentence(code: String, party: String = "", begin: Int = 0, end: Int = 0, content: String = "") {
  require(code.nonEmpty)
  
  def formatTime(value: Int) =
    new org.joda.time.DateTime(value, org.joda.time.DateTimeZone.UTC).toString("HH:mm:ss.SSS")

  lazy val subtitle = s"""$party-$begin ${formatTime(begin)} --> ${formatTime(end)} <v $code>$content</v>\n"""

  lazy val text = s"$party-$begin $content"
}
