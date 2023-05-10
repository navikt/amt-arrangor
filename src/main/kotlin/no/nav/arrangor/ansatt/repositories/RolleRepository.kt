package no.nav.arrangor.ansatt.repositories

import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.utils.getNullableZonedDateTime
import no.nav.arrangor.utils.getZonedDateTime
import no.nav.arrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@Repository
class RolleRepository(
    private val template: NamedParameterJdbcTemplate
) {

    private val rowMapper = RowMapper { rs, _ ->
        RolleDbo(
            id = rs.getInt("id"),
            ansattId = UUID.fromString(rs.getString("ansatt_id")),
            arrangorId = UUID.fromString(rs.getString("arrangor_id")),
            organisasjonsnummer = rs.getString("organisasjonsnummer"),
            rolle = AnsattRolle.valueOf(rs.getString("rolle")),
            gyldigFra = rs.getZonedDateTime("gyldig_fra"),
            gyldigTil = rs.getNullableZonedDateTime("gyldig_til")
        )
    }

    fun leggTilRoller(input: List<RolleInput>) {
        if (input.isEmpty()) return

        val sql = """
            INSERT INTO ansatt_rolle(ansatt_id, arrangor_id, rolle)
            VALUES (:ansatt_id,
                    (SELECT id FROM arrangor WHERE organisasjonsnummer = :organisasjonsnummer),
                    :rolle)
        """.trimIndent()

        template.batchUpdate(
            sql,
            input.map {
                sqlParameters(
                    "ansatt_id" to it.ansattId,
                    "organisasjonsnummer" to it.organisasjonsnummer,
                    "rolle" to it.rolle.name
                )
            }.toTypedArray()
        )
    }

    fun deaktiverRoller(ids: List<Int>) {
        if (ids.isNotEmpty()) {
            template.update(
                "UPDATE ansatt_rolle SET gyldig_til = current_timestamp WHERE id in (:ids)",
                sqlParameters("ids" to ids)
            )
        }
    }

    fun getAktiveRoller(ansattId: UUID): List<RolleDbo> {
        val sql = """
            SELECT ansatt_rolle.*,
                   arrangor.organisasjonsnummer as organisasjonsnummer
            FROM ansatt_rolle 
            left join arrangor on ansatt_rolle.arrangor_id = arrangor.id 
            WHERE ansatt_id = :ansatt_id
            AND gyldig_til is NULL
        """.trimIndent()

        return template.query(
            sql,
            sqlParameters("ansatt_id" to ansattId),
            rowMapper
        )
    }

    fun getAll(ansattId: UUID): List<RolleDbo> = template.query(
        """
            SELECT ansatt_rolle.*,
                   arrangor.organisasjonsnummer as organisasjonsnummer
            FROM ansatt_rolle 
            left join arrangor on ansatt_rolle.arrangor_id = arrangor.id 
            WHERE ansatt_id = :ansatt_id
        """.trimIndent(),
        sqlParameters("ansatt_id" to ansattId),
        rowMapper
    )


    data class RolleInput(
        val ansattId: UUID,
        val organisasjonsnummer: String,
        val rolle: AnsattRolle
    )

    data class RolleDbo(
        val id: Int = -1,
        val ansattId: UUID,
        val arrangorId: UUID,
        val organisasjonsnummer: String,
        val rolle: AnsattRolle,
        val gyldigFra: ZonedDateTime = ZonedDateTime.now(),
        val gyldigTil: ZonedDateTime? = null
    )
}
