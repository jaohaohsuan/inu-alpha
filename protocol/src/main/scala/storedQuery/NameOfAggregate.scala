package protocol.storedQuery


case class Name(name: String, proxy: String) {
  lazy val manager = s"/user/$name"
  lazy val client = s"/user/$proxy"

  override def toString = name
}

object NameOfAggregate {

  val view = Name("stored-query-aggregate-root-view", "stored-query-aggregate-root-view-proxy")

  val Root = "stored-query-aggregate-root"


}
