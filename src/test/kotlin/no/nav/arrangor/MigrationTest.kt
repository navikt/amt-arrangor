package no.nav.arrangor

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class MigrationTest(
    private val dataSource: DataSource,
) : RepositoryTestBase() {
    private val jdbcTemplate by lazy { JdbcTemplate(dataSource) }

    @Test
    fun `V14 - normaliserer og dedupliserer ansatt arrangordata`() {
        val arrangor = testDatabase.insertArrangor()
        val ansatt = testDatabase.insertAnsatt(arrangorer = emptyList())
        val gyldigFra = "2025-10-16T09:42:34.123456Z"
        val gyldigTil = "2025-10-16T09:42:34.994471Z"
        val json =
            """
            [
              {
                "arrangorId": "${arrangor.id}",
                "roller": [
                  {
                    "rolle": "VEILEDER",
                    "gyldigFra": "$gyldigFra",
                    "gyldigTil": "2025-10-16T09:42:34.994467Z"
                  },
                  {
                    "rolle": "VEILEDER",
                    "gyldigFra": "$gyldigFra",
                    "gyldigTil": "$gyldigTil"
                  },
                  {
                    "rolle": "KOORDINATOR",
                    "gyldigFra": "2025-01-01T00:00:00Z",
                    "gyldigTil": null
                  }
                ],
                "veileder": [
                  {
                    "deltakerId": "${UUID.randomUUID()}",
                    "veilederType": "VEILEDER",
                    "gyldigFra": "2026-11-10T14:36:05.504509103+01:00[Europe/Oslo]",
                    "gyldigTil": "2026-11-10T14:36:05.504509103+01:00[Europe/Oslo]"
                  },
                  {
                    "deltakerId": "${UUID.randomUUID()}",
                    "veilederType": "MEDVEILEDER",
                    "gyldigFra": "2026-11-10T14:36:05.504509103Z",
                    "gyldigTil": null
                  }
                ],
                "koordinator": [
                  {
                    "deltakerlisteId": "${UUID.randomUUID()}",
                    "gyldigFra": "2025-01-01T00:00:00Z",
                    "gyldigTil": null
                  }
                ]
              }
            ]
            """.trimIndent()

        jdbcTemplate.update(
            "UPDATE ansatt SET arrangorer = ?::jsonb WHERE id = ?",
            json,
            ansatt.id,
        )

        ResourceDatabasePopulator(
            ClassPathResource("db/migration/V14__populate_ansatt_arrangor_tables.sql"),
        ).execute(dataSource)

        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ansatt_arrangor WHERE ansatt_id = ?",
            Int::class.java,
            ansatt.id,
        ) shouldBe 1
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ansatt_arrangor_rolle WHERE ansatt_id = ?",
            Int::class.java,
            ansatt.id,
        ) shouldBe 2
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ansatt_arrangor_veileder WHERE ansatt_id = ?",
            Int::class.java,
            ansatt.id,
        ) shouldBe 2
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ansatt_arrangor_koordinator WHERE ansatt_id = ?",
            Int::class.java,
            ansatt.id,
        ) shouldBe 1

        jdbcTemplate.queryForObject(
            "SELECT gyldig_til FROM ansatt_arrangor_rolle WHERE ansatt_id = ? AND rolle = 'VEILEDER'",
            Timestamp::class.java,
            ansatt.id,
        ) shouldBe Timestamp.from(OffsetDateTime.parse(gyldigTil).toInstant())

        jdbcTemplate.queryForObject(
            "SELECT gyldig_til FROM ansatt_arrangor_rolle WHERE ansatt_id = ? AND rolle = 'KOORDINATOR'",
            Timestamp::class.java,
            ansatt.id,
        ) shouldBe null

        jdbcTemplate.queryForObject(
            "SELECT gyldig_til FROM ansatt_arrangor_veileder WHERE ansatt_id = ? AND veileder_type = 'VEILEDER'",
            Timestamp::class.java,
            ansatt.id,
        ) shouldBe Timestamp.from(
            OffsetDateTime
                .parse("2026-11-10T14:36:05.504509103+01:00")
                .toInstant()
                .let { Instant.ofEpochSecond(it.epochSecond, (it.nano / 1000) * 1000L) },
        )

        jdbcTemplate.queryForObject(
            "SELECT gyldig_til FROM ansatt_arrangor_veileder WHERE ansatt_id = ? AND veileder_type = 'MEDVEILEDER'",
            Timestamp::class.java,
            ansatt.id,
        ) shouldBe null

        jdbcTemplate.query(
            "SELECT arrangor_id FROM ansatt_arrangor WHERE ansatt_id = ?",
            { rs, _ -> UUID.fromString(rs.getString("arrangor_id")) },
            ansatt.id,
        ) shouldContainExactly listOf(arrangor.id)
    }
}
