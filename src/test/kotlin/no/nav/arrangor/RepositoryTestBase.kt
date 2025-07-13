package no.nav.arrangor

import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.database.DbTestDataUtils
import no.nav.arrangor.database.SingletonPostgresContainer.postgresContainer
import no.nav.arrangor.database.TestDatabaseService
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import javax.sql.DataSource

@ActiveProfiles("test")
@SpringBootTest(classes = [AnsattRepository::class, ArrangorRepository::class, TestDatabaseService::class])
@AutoConfigureJdbc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class RepositoryTestBase {
	@Autowired
	private lateinit var dataSource: DataSource

	@Autowired
	protected lateinit var testDatabase: TestDatabaseService

	@AfterEach
	fun cleanDatabase() = DbTestDataUtils.cleanDatabase(dataSource)

	companion object {
		@ServiceConnection
		@Suppress("unused")
		private val container = postgresContainer
	}
}
