package protocol.storedQuery

/**
 * Created by henry on 9/18/15.
 */
object AggregateRootClient {

  private val address = s"/user/${AggregateRoot.Name}"
/*
  def PullChanges = ClusterClient.SendToAll(address, Pull)

  def SendToAllRegisterQueryOK(changes: Set[(String, Int)]) = ClusterClient.SendToAll(address, RegisterQueryOK(changes))*/
}
