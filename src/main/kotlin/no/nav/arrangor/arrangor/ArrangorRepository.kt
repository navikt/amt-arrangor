package no.nav.arrangor.arrangor

import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.utils.getNullableUUID
import no.nav.arrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class ArrangorRepository(
	private val template: NamedParameterJdbcTemplate
) {

	private val rowMapper = RowMapper { rs, _ ->
		ArrangorDbo(
			id = UUID.fromString(rs.getString("id")),
			navn = rs.getString("navn"),
			organisasjonsnummer = rs.getString("organisasjonsnummer"),
			overordnetArrangorId = rs.getNullableUUID("overordnet_arrangor_id")
		)
	}

	fun insertOrUpdate(arrangorInput: ArrangorDbo): ArrangorDbo {
		val sql = """
		INSERT INTO arrangor(id, navn, organisasjonsnummer, overordnet_arrangor_id)
		VALUES (:id,
		        :navn,
		        :organisasjonsnummer,
		        :overordnet_arrangor_id)
		ON CONFLICT (organisasjonsnummer) DO UPDATE SET
		        navn     							 = :navn,
		        overordnet_arrangor_id 			     = :overordnet_arrangor_id,
		        last_synchronized                    = current_timestamp
		RETURNING *
		""".trimIndent()

		return template.query(
			sql,
			sqlParameters(
				"id" to arrangorInput.id,
				"navn" to arrangorInput.navn,
				"organisasjonsnummer" to arrangorInput.organisasjonsnummer,
				"overordnet_arrangor_id" to arrangorInput.overordnetArrangorId
			),
			rowMapper
		)
			.first()
	}

	fun get(id: UUID): ArrangorDbo? = template.query(
		"SELECT * FROM arrangor WHERE id = :id",
		sqlParameters("id" to id),
		rowMapper
	).firstOrNull()

	fun get(orgNr: String): ArrangorDbo? = template.query(
		"SELECT * FROM arrangor WHERE organisasjonsnummer = :organisasjonsnummer",
		sqlParameters("organisasjonsnummer" to orgNr),
		rowMapper
	).firstOrNull()

	fun getOrganiasjonsnummerForId(id: UUID): String? = template.query(
		"SELECT * FROM arrangor where id = :id",
		sqlParameters("id" to id),
		rowMapper
	).firstOrNull()
		?.organisasjonsnummer

	fun getToSynchronize(maxSize: Int, synchronizedBefore: LocalDateTime): List<ArrangorDbo> {
		val sql = """
		SELECT *
		FROM arrangor
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

	data class ArrangorDbo(
		val id: UUID = UUID.randomUUID(),
		val navn: String,
		val organisasjonsnummer: String,
		val overordnetArrangorId: UUID?
	) {
		fun toDomain(): Arrangor = Arrangor(
			id = id,
			navn = navn,
			organisasjonsnummer = organisasjonsnummer,
			overordnetArrangorId = overordnetArrangorId
		)
	}
}
