package no.nav.arrangor.ansatt.repositories

import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.testutils.SingletonPostgresContainer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID

class VeilederDeltakerRepositoryTest {

    private val datasource = SingletonPostgresContainer.getDataSource()
    private val template = NamedParameterJdbcTemplate(datasource)

    private val repository = VeilederDeltakerRepository(template)
    lateinit var db: DbTestData

    @BeforeEach
    fun setUp() {
        db = DbTestData(template)
    }

    @AfterEach
    fun tearDown() {
        DbTestDataUtils.cleanDatabase(datasource)
    }

    @Test
    fun `leggTil - Legger til`() {
        val ansatt = db.insertAnsatt()
        repository.leggTil(
            ansatt.id,
            deltakere = listOf(
                lagDeltaker(),
                lagDeltaker()
            )
        )
    }

    private fun lagDeltaker(
        deltakerId: UUID = UUID.randomUUID(),
        veilederType: VeilederType = VeilederType.VEILEDER
    ): VeilederDeltakerRepository.VeilederDeltakerInput = VeilederDeltakerRepository.VeilederDeltakerInput(
        deltakerId,
        veilederType
    )
}
