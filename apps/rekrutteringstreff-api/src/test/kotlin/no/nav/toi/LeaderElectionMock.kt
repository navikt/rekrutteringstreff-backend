package no.nav.toi

class LeaderElectionMock : LeaderElectionInterface {
    override fun isLeader(): Boolean = true
}
