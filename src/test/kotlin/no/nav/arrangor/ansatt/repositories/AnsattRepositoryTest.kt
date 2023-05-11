package no.nav.arrangor.ansatt.repositories

import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.testutils.SingletonPostgresContainer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

class AnsattRepositoryTest {

	private val datasource = SingletonPostgresContainer.getDataSource()
	private val template = NamedParameterJdbcTemplate(datasource)

	private val repository = AnsattRepository(template)
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
	fun `insertOrUpdate - not exists - adds to database`() {
		val ansatt = AnsattRepository.AnsattDbo(
			personident = "123456",
			personId = UUID.randomUUID(),
			fornavn = "Test",
			mellomnavn = "Mellom",
			etternavn = "Testersen"
		)

		val inserted = repository.insertOrUpdate(ansatt)

		inserted.id shouldNotBe -1
		inserted.personident shouldBe ansatt.personident
		inserted.fornavn shouldBe ansatt.fornavn
		inserted.mellomnavn shouldBe ansatt.mellomnavn
		inserted.etternavn shouldBe ansatt.etternavn
	}

	@Test
	fun `insertOrUpdate - exists - updates`() {
		val old = AnsattRepository.AnsattDbo(
			personident = "123456",
			personId = UUID.randomUUID(),
			fornavn = "Test",
			mellomnavn = "Mellom",
			etternavn = "Testersen"
		).let { repository.insertOrUpdate(it) }

		val new = AnsattRepository.AnsattDbo(
			personident = old.personident,
			personId = old.personId,
			fornavn = "Test2",
			mellomnavn = null,
			etternavn = "Testersen2"
		).let { repository.insertOrUpdate(it) }

		new.id shouldBe old.id
		new.personident shouldBe old.personident
		new.fornavn shouldBe "Test2"
		new.mellomnavn shouldBe null
		new.etternavn shouldBe "Testersen2"
	}

	@Test
	fun `get(UUID) - not exists - returns null`() {
		repository.get(UUID.randomUUID()) shouldBe null
	}

	@Test
	fun `get(UUID) - exists - returns Ansatt`() {
		val stored = AnsattRepository.AnsattDbo(
			personident = "123456",
			personId = UUID.randomUUID(),
			fornavn = "Test",
			mellomnavn = "Mellom",
			etternavn = "Testersen"
		).let { repository.insertOrUpdate(it) }

		repository.get(stored.id) shouldBe stored
	}

	@Test
	fun `get(String) - not exists - returns null`() {
		repository.get(UUID.randomUUID().toString()) shouldBe null
	}

	@Test
	fun `get(String) - exists - returns Ansatt`() {
		val stored = AnsattRepository.AnsattDbo(
			personident = "123456",
			personId = UUID.randomUUID(),
			fornavn = "Test",
			mellomnavn = "Mellom",
			etternavn = "Testersen"
		).let { repository.insertOrUpdate(it) }

		repository.get(stored.personident) shouldBe stored
	}

	@Test
	fun `setSynchronized - one entry - sets field to current_timestamp`() {
		val stored = db.ansatt(lastSynchronized = LocalDateTime.now().minusDays(1))
			.let { repository.insertOrUpdate(it) }

		repository.setSynchronized(stored.id)

		val gotten = repository.get(stored.id)

		stored.lastSynchronized shouldNotBe gotten?.lastSynchronized
	}

	@Test
	fun `setSynchronized - list - sets all fields to current_timestamp`() {
		val one = db.ansatt(lastSynchronized = LocalDateTime.now().minusDays(1)).let { repository.insertOrUpdate(it) }
		val two = db.ansatt(lastSynchronized = LocalDateTime.now().minusDays(2)).let { repository.insertOrUpdate(it) }

		repository.setSynchronized(listOf(one.id, two.id))

		repository.get(one.id) shouldNotBe one.lastSynchronized
		repository.get(two.id) shouldNotBe two.lastSynchronized
	}

	@Test
	fun `getToSynchronize - Returns only values to synchronize`() {
		db.ansatt().let { repository.insertOrUpdate(it) }
		val two = db.ansatt(lastSynchronized = LocalDateTime.now().minusDays(8)).let { repository.insertOrUpdate(it) }

		val returned = repository.getToSynchronize(5, LocalDateTime.now().minusDays(7))

		returned.size shouldBe 1
		returned[0] shouldBe two
	}

	@Test
	fun `getToSynchronize - returns max limit and oldest ordered`() {
		db.ansatt().let { repository.insertOrUpdate(it) }
		val two = db.ansatt(lastSynchronized = LocalDateTime.now().minusDays(2)).let { repository.insertOrUpdate(it) }
		val three = db.ansatt(lastSynchronized = LocalDateTime.now().minusDays(1)).let { repository.insertOrUpdate(it) }

		val returned = repository.getToSynchronize(2, LocalDateTime.now().plusMinutes(7))

		returned.size shouldBe 2
		returned shouldContainInOrder listOf(two, three)
	}
}
