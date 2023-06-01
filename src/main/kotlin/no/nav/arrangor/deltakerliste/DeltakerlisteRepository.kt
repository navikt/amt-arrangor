package no.nav.arrangor.deltakerliste

import no.nav.arrangor.utils.sqlParameters
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DeltakerlisteRepository(
	private val template: NamedParameterJdbcTemplate
) {

	fun upsertDeltakerliste(arrangorId: UUID, deltakerlisteId: UUID) {
		val sql = """
		INSERT INTO deltakerliste(id, arrangor_id)
		    VALUES (:id, :arrangor_id)
		ON CONFLICT (id) DO UPDATE SET arrangor_id = :arrangor_id,
									   modified_at = current_timestamp
		""".trimIndent()

		template.update(
			sql,
			sqlParameters("id" to deltakerlisteId, "arrangor_id" to arrangorId)
		)
	}

	fun getDeltakerlisterForArrangor(arrangorId: UUID): Set<UUID> = template.query(
		"SELECT * FROM deltakerliste where arrangor_id = :arrangor_id",
		sqlParameters("arrangor_id" to arrangorId)
	) { rs, _ -> UUID.fromString(rs.getString("id")) }.toSet()
}
