package no.nav.toi

/**
 * Hvilket miljø appen kjører i. Brukes til å holde funksjonalitet under utvikling
 * borte fra produksjon, på samme måte som frontend gjør det via vertsnavnet.
 *
 * Lokal kjøring har ingen `NAIS_CLUSTER_NAME`, og regnes derfor som [LOKALT].
 */
enum class Miljø {
    LOKALT,
    DEV_GCP,
    PROD_GCP;

    val erProd: Boolean get() = this == PROD_GCP

    companion object {
        fun fraClusterNavn(clusterNavn: String?): Miljø = when {
            clusterNavn.isNullOrBlank() || clusterNavn == "local" || clusterNavn == "lokalt" -> LOKALT
            clusterNavn == "dev-gcp" -> DEV_GCP
            clusterNavn == "prod-gcp" -> PROD_GCP
            else -> PROD_GCP // FOr å hindre at uferdig kode komer i nytt miljø. Kan brukes så lenge ikke det er funksjonalitet som kun skal brukes i produksjon som er caset.
        }
    }
}
