package no.nav.toi

import no.nav.toi.LeaderElectionInterface

class LeaderElectionMock : LeaderElectionInterface {
    override fun isLeader(): Boolean = true
}
