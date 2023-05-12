package no.nav.arrangor.deltakerliste

import no.nav.arrangor.utils.sqlParameters
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DeltakerlisteRepository(
	private val template: NamedParameterJdbcTemplate
) {

	fun addUpdateDeltakerlister(arrangorId: UUID, deltakerlisteIds: Set<UUID>) {
		val sql = """
		INSERT INTO deltakerliste(id, arrangor_id)
		    VALUES (:id, :arrangor_id)
		ON CONFLICT (id) DO UPDATE SET arrangor_id = :arrangor_id,
									   modified_at = current_timestamp
		""".trimIndent()

		template.batchUpdate(
			sql,
			deltakerlisteIds.map { sqlParameters("id" to it, "arrangor_id" to arrangorId) }.toTypedArray()
		)
	}

	fun getDeltakerlisterForArrangor(arrangorId: UUID): Set<UUID> = template.query(
		"SELECT * FROM deltakerliste where arrangor_id = :arrangor_id",
		sqlParameters("arrangor_id" to arrangorId)
	) { rs, _ -> UUID.fromString(rs.getString("id")) }.toSet()
}
