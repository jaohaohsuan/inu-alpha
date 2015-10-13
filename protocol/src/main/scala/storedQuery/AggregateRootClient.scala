package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
object AggregateRootClient {

  private val address = s"/user/${NameOfAggregate.Root}"
/*
  def PullChanges = ClusterClient.SendToAll(address, Pull)

  def SendToAllRegisterQueryOK(changes: Set[(String, Int)]) = ClusterClient.SendToAll(address, RegisterQueryOK(changes))*/
}

object AggregateRootViewClient {

}
