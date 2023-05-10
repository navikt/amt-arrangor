package no.nav.arrangor.ansatt.repositories

import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.utils.getNullableZonedDateTime
import no.nav.arrangor.utils.getZonedDateTime
import no.nav.arrangor.utils.sqlParameters
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@Repository
class VeilederDeltakerRepository(
    private val template: NamedParameterJdbcTemplate
) {

    private val rowMapper = RowMapper { rs, _ ->
        VeilederDeltakerDbo(
            id = rs.getInt("id"),
            ansattId = UUID.fromString(rs.getString("ansatt_id")),
            deltakerId = UUID.fromString(rs.getString("deltaker_id")),
            veilederType = VeilederType.valueOf(rs.getString("veileder_type")),
            gyldigFra = rs.getZonedDateTime("gyldig_fra"),
            gyldigTil = rs.getNullableZonedDateTime("gyldig_til")
        )
    }

    fun leggTil(ansattId: UUID, deltakere: List<VeilederDeltakerInput>) {
        val sql = """
            INSERT INTO veileder_deltaker(ansatt_id, deltaker_id, veileder_type)
            VALUES(:ansatt_id, :deltaker_id, :veileder_type)
        """.trimIndent()

        if (deltakere.isNotEmpty()) {
            template.batchUpdate(
                sql,
                deltakere.map { deltaker ->
                    sqlParameters(
                        "ansatt_id" to ansattId,
                        "deltaker_id" to deltaker.deltakerId,
                        "veileder_type" to deltaker.veilederType.name
                    )
                }.toTypedArray()
            )
        }
    }

    fun deaktiver(ids: List<Int>) {
        if (ids.isNotEmpty()) {
            template.update(
                "UPDATE veileder_deltaker SET gyldig_til = current_timestamp WHERE id in (:ids)",
                sqlParameters("ids" to ids)
            )
        }
    }

    fun getAktive(ansattId: UUID): List<VeilederDeltakerDbo> {
        return template.query(
            "SELECT * FROM veileder_deltaker WHERE ansatt_id = :ansatt_id AND gyldig_til IS NULL",
            sqlParameters("ansatt_id" to ansattId),
            rowMapper
        )
    }

    fun getAll(ansattId: UUID): List<VeilederDeltakerDbo> = template.query(
        "SELECT * FROM veileder_deltaker WHERE ansatt_id = :ansatt_id",
        sqlParameters("ansatt_id" to ansattId),
        rowMapper
    )

    data class VeilederDeltakerInput(
        val deltakerId: UUID,
        val veilederType: VeilederType
    )

    data class VeilederDeltakerDbo(
        val id: Int,
        val ansattId: UUID,
        val deltakerId: UUID,
        val veilederType: VeilederType,
        val gyldigFra: ZonedDateTime,
        val gyldigTil: ZonedDateTime?

    )
}
