import no.nav.toi.LeaderElectionInterface

interface LeaderElectionInterface {
    fun isLeader(): Boolean
}

class LeaderElectionMock : LeaderElectionInterface {
    override fun isLeader(): Boolean = true
}
