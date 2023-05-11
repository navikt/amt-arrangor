package no.nav.arrangor.utils

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

fun <V> sqlParameters(vararg pairs: Pair<String, V>): MapSqlParameterSource =
    MapSqlParameterSource().addValues(pairs.toMap())

fun ResultSet.getNullableUUID(columnLabel: String): UUID? =
    this.getString(columnLabel)?.let { UUID.fromString(it) }

fun ResultSet.getNullableZonedDateTime(columnLabel: String): ZonedDateTime? =
    this.getTimestamp(columnLabel)?.let {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.time), ZoneOffset.systemDefault())
    }

fun ResultSet.getZonedDateTime(columnLabel: String): ZonedDateTime =
    getNullableZonedDateTime(columnLabel)
        ?: throw IllegalStateException("Expected $columnLabel not to be null")
