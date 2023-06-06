package no.nav.arrangor.utils

fun isDev(): Boolean {
	val cluster = System.getenv("NAIS_CLUSTER_NAME") ?: "Ikke dev"
	return cluster == "dev-gcp"
}
