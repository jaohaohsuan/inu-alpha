package protocol.clustering

case class Name(name: String, proxy: String) {
  lazy val manager = s"/user/$name"
  lazy val client = s"/user/$proxy"

  override def toString = name
}