package no.nav.toi.statistikk

import java.time.LocalDate

class StatistikkService(
    private val statistikkRepository: StatistikkRepository,
) {
    fun hentFåttJobbStatistikk(navKontor: String, fraOgMed: LocalDate, tilOgMed: LocalDate): FåttJobbStatistikk =
        statistikkRepository.hentFåttJobbStatistikk(navKontor, fraOgMed, tilOgMed)
}
