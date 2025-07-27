package no.nav.arrangor.utils

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

fun <V> sqlParameters(vararg pairs: Pair<String, V>): MapSqlParameterSource = MapSqlParameterSource().addValues(pairs.toMap())

fun ResultSet.getNullableUUID(columnLabel: String): UUID? = this.getString(columnLabel)?.let { UUID.fromString(it) }

fun Timestamp.toSystemZoneLocalDateTime(): LocalDateTime = this
	.toInstant()
	.atZone(ZoneId.systemDefault())
	.toLocalDateTime()
