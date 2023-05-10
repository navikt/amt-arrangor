package no.nav.arrangor.ansatt.repositories

import no.nav.arrangor.utils.getNullableZonedDateTime
import no.nav.arrangor.utils.getZonedDateTime
import no.nav.arrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@Repository
class KoordinatorDeltakerlisteRepository(
    private val template: NamedParameterJdbcTemplate
) {

    private val rowMapper = RowMapper { rs, _ ->
        KoordinatorDeltakerlisteDbo(
            id = rs.getInt("id"),
            ansattId = UUID.fromString(rs.getString("ansatt_id")),
            deltakerlisteId = UUID.fromString(rs.getString("deltakerliste_id")),
            gyldigFra = rs.getZonedDateTime("gyldig_fra"),
            gyldigTil = rs.getNullableZonedDateTime("gyldig_til")
        )
    }

    fun leggTilKoordinatorDeltakerlister(ansattId: UUID, deltakerlisteIds: List<UUID>) {
        val sql = """
            INSERT INTO koordinator_deltakerliste(ansatt_id, deltakerliste_id)
            VALUES(:ansatt_id, :deltakerliste_id)
        """.trimIndent()

        if (deltakerlisteIds.isNotEmpty()) {
            template.batchUpdate(
                sql,
                deltakerlisteIds.map { deltakerlisteId ->
                    sqlParameters(
                        "ansatt_id" to ansattId,
                        "deltakerliste_id" to deltakerlisteId
                    )
                }.toTypedArray()
            )
        }
    }

    fun deaktiverKoordinatorDeltakerliste(ids: List<Int>) {
        if (ids.isNotEmpty()) {
            template.update(
                "UPDATE koordinator_deltakerliste SET gyldig_til = current_timestamp WHERE id in (:ids)",
                sqlParameters("ids" to ids)
            )
        }
    }

    fun getAktive(ansattId: UUID): List<KoordinatorDeltakerlisteDbo> {
        return template.query(
            "SELECT * FROM koordinator_deltakerliste WHERE ansatt_id = :ansatt_id AND gyldig_til IS NULL",
            sqlParameters("ansatt_id" to ansattId),
            rowMapper
        )
    }

    fun getAll(ansattId: UUID): List<KoordinatorDeltakerlisteDbo> = template.query(
        "SELECT * FROM koordinator_deltakerliste WHERE ansatt_id = :ansatt_id",
        sqlParameters("ansatt_id" to ansattId),
        rowMapper
    )

    data class KoordinatorDeltakerlisteDbo(
        val id: Int,
        val ansattId: UUID,
        val deltakerlisteId: UUID,
        val gyldigFra: ZonedDateTime,
        val gyldigTil: ZonedDateTime?
    )
}
