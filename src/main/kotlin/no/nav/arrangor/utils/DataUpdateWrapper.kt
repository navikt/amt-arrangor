package no.nav.arrangor.utils

data class DataUpdateWrapper<T>(
	val isUpdated: Boolean,
	val data: T
)
