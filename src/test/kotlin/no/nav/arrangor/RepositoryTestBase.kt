package no.nav.arrangor

import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.testutils.SingletonPostgresContainer.postgresContainer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

@ActiveProfiles("test")
@AutoConfigureJdbc
abstract class RepositoryTestBase {
	@Autowired
	private lateinit var dataSource: DataSource

	@Autowired
	private lateinit var template: NamedParameterJdbcTemplate

	@AfterEach
	fun cleanDatabase() = DbTestDataUtils.cleanDatabase(dataSource)

	protected lateinit var testDatabase: DbTestData

	@BeforeEach
	fun setUpTestDatabase() {
		testDatabase = DbTestData(template)
	}

	companion object {
		@ServiceConnection
		@Suppress("unused")
		private val container = postgresContainer
	}
}
