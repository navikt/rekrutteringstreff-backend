package no.nav.toi.minside

import no.nav.toi.LeaderElectionInterface

class LeaderElectionMock : LeaderElectionInterface {
    override fun isLeader(): Boolean = true
}
