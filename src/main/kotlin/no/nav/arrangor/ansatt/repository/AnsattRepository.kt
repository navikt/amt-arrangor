package no.nav.arrangor.ansatt.repository

import no.nav.arrangor.utils.JsonUtils
import no.nav.arrangor.utils.getZonedDateTime
import no.nav.arrangor.utils.sqlParameters
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class AnsattRepository(
	private val template: NamedParameterJdbcTemplate
) {
	private val rowMapper = RowMapper { rs, _ ->
		AnsattDbo(
			id = UUID.fromString(rs.getString("id")),
			personId = UUID.fromString(rs.getString("person_id")),
			personident = rs.getString("personident"),
			fornavn = rs.getString("fornavn"),
			mellomnavn = rs.getString("mellomnavn"),
			etternavn = rs.getString("etternavn"),
			arrangorer = JsonUtils.fromJson(rs.getString("arrangorer")),
			modifiedAt = rs.getZonedDateTime("modified_at").toLocalDateTime(),
			lastSynchronized = rs.getZonedDateTime("last_synchronized").toLocalDateTime()
		)
	}

	fun insertOrUpdate(ansatt: AnsattDbo): AnsattDbo {
		val sql = """
		INSERT INTO ansatt(id, person_id, personident, fornavn, mellomnavn, etternavn, arrangorer, modified_at, last_synchronized)
		VALUES (:id,
		        :person_id,
		        :personident,
		        :fornavn,
		        :mellomnavn,
		        :etternavn,
				:arrangorer,
		        :modified_at,
		        :last_synchronized)
		ON CONFLICT (personident) DO UPDATE SET
		    fornavn           = :fornavn,
		    mellomnavn        = :mellomnavn,
		    etternavn         = :etternavn,
			arrangorer		  = :arrangorer,
		    modified_at       = current_timestamp,
		    last_synchronized = :last_synchronized
		RETURNING *
		""".trimIndent()

		return template.query(
			sql,
			sqlParameters(
				"id" to ansatt.id,
				"person_id" to (ansatt.personId),
				"personident" to ansatt.personident,
				"fornavn" to ansatt.fornavn,
				"mellomnavn" to ansatt.mellomnavn,
				"etternavn" to ansatt.etternavn,
				"arrangorer" to ansatt.arrangorer.toPGObject(),
				"modified_at" to ansatt.modifiedAt,
				"last_synchronized" to ansatt.lastSynchronized
			),
			rowMapper
		).first()
	}

	fun get(id: UUID): AnsattDbo? = template.query(
		"SELECT * FROM ansatt WHERE id = :id",
		sqlParameters("id" to id),
		rowMapper
	).firstOrNull()

	fun getAnsatte(ider: List<UUID>): List<AnsattDbo> {
		if (ider.isEmpty()) {
			return emptyList()
		}
		return template.query(
			"SELECT * FROM ansatt WHERE id in(:ids)",
			sqlParameters("ids" to ider),
			rowMapper
		)
	}

	fun get(personident: String): AnsattDbo? = template.query(
		"SELECT * from ansatt where personident = :personident",
		sqlParameters("personident" to personident),
		rowMapper
	).firstOrNull()

	fun setSynchronized(id: UUID, timestamp: LocalDateTime) = template.update(
		"UPDATE ansatt SET last_synchronized = :last_synchronized WHERE id = :id",
		sqlParameters(
			"id" to id,
			"last_synchronized" to timestamp
		)
	)

	fun getToSynchronize(maxSize: Int, synchronizedBefore: LocalDateTime): List<AnsattDbo> {
		val sql = """
		SELECT *
		FROM ansatt
		WHERE last_synchronized < :synchronized_before
		ORDER BY last_synchronized asc
		limit :limit
		""".trimIndent()

		val parameters = sqlParameters(
			"limit" to maxSize,
			"synchronized_before" to synchronizedBefore
		)

		return template.query(sql, parameters, rowMapper)
	}
}

fun List<ArrangorDbo>.toPGObject() = PGobject().also {
	it.type = "json"
	it.value = JsonUtils.objectMapper().writeValueAsString(this)
}
