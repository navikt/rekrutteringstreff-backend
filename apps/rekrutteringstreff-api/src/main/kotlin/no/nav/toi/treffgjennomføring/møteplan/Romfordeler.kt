package no.nav.toi.treffgjennomføring.møteplan

import no.nav.toi.jobbsoker.PersonTreffId

object Romfordeler {

    fun fordelJevnt(jobbsøkere: List<PersonTreffId>, antallRom: Int): List<Rom> =
        if (antallRom <= 0) emptyList()
        else (1..antallRom).map { romnummer ->
            Rom(romnummer, jobbsøkere.filterIndexed { indeks, _ -> indeks % antallRom == romnummer - 1 })
        }

    fun oppdaterEtterOppmøte(rom: List<Rom>, oppmøte: List<PersonTreffId>): List<Rom> {
        if (rom.isEmpty()) return emptyList()

        val fremmøtte = oppmøte.toSet()
        val fordelt = mutableSetOf<PersonTreffId>()
        val oppdatert = rom.map { r ->
            r.romnummer to r.jobbsøkere.filter { it in fremmøtte && fordelt.add(it) }.toMutableList()
        }

        oppmøte.filterNot { it in fordelt }.forEach { person ->
            oppdatert.minBy { it.second.size }.second.add(person)
        }

        return oppdatert.map { (romnummer, jobbsøkere) -> Rom(romnummer, jobbsøkere) }
    }

    fun normaliser(rom: List<Rom>, antallRom: Int): List<Rom> {
        if (antallRom <= 0) return emptyList()

        val beholdt = (1..antallRom).map { romnummer ->
            romnummer to rom.firstOrNull { it.romnummer == romnummer }?.jobbsøkere.orEmpty().toMutableList()
        }
        rom.filter { it.romnummer > antallRom }.flatMap { it.jobbsøkere }.forEach { person ->
            beholdt.minBy { it.second.size }.second.add(person)
        }

        return beholdt.map { (romnummer, jobbsøkere) -> Rom(romnummer, jobbsøkere) }
    }
}
