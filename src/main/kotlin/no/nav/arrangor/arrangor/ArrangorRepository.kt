package no.nav.arrangor.arrangor

import no.nav.arrangor.arrangor.domain.Arrangor
import no.nav.arrangor.utils.getNullableUUID
import no.nav.arrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class ArrangorRepository(
    private val template: NamedParameterJdbcTemplate
) {

    private val rowMapper = RowMapper { rs, _ ->
        Arrangor(
            id = UUID.fromString(rs.getString("id")),
            navn = rs.getString("navn"),
            organisasjonsnummer = rs.getString("organisasjonsnummer"),
            overordnetArrangorId = rs.getNullableUUID("overordnet_arrangor_id")
        )
    }

    fun insertOrUpdateArrangor(arrangorInput: ArrangorInput): Arrangor {
        val sql = """
        INSERT INTO arrangor(id, navn, organisasjonsnummer, overordnet_arrangor_id)
        VALUES (:id,
                :navn,
                :organisasjonsnummer,
                :overordnet_arrangor_id)
        ON CONFLICT (organisasjonsnummer) DO UPDATE SET
                navn     							 = :navn,
                overordnet_arrangor_id 			     = :overordnet_arrangor_id
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

    fun get(id: UUID): Arrangor = template.query(
        "SELECT * FROM arrangor WHERE id = :id",
        sqlParameters("id" to id.toString()),
        rowMapper
    ).first()

    fun get(orgNr: String): Arrangor? = template.query(
        "SELECT * FROM arrangor WHERE organisasjonsnummer = :organisasjonsnummer",
        sqlParameters("organisasjonsnummer" to orgNr),
        rowMapper
    ).firstOrNull()

    data class ArrangorInput(
        val id: UUID = UUID.randomUUID(),
        val navn: String,
        val organisasjonsnummer: String,
        val overordnetArrangorId: UUID?
    )
}
