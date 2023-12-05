package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import no.nav.arrangor.arrangor.ArrangorRepository.ArrangorDbo
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.testutils.SingletonPostgresContainer
import org.junit.AfterClass
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID

class ArrangorRepositoryTest {
	companion object {
		private val datasource = SingletonPostgresContainer.getDataSource()
		private val template = NamedParameterJdbcTemplate(datasource)

		private val repository = ArrangorRepository(template)

		@JvmStatic
		@AfterClass
		fun tearDown() {
			DbTestDataUtils.cleanDatabase(datasource)
		}
	}

	@Test
	fun `insertOrUpdate - arrangor finnes ikke - inserter og returnerer arrangor`() {
		val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
		repository.insertOrUpdate(arrangor) shouldBe arrangor
		repository.get(arrangor.id) shouldBe arrangor
	}

	@Test
	fun `insertOrUpdate - arrangor finnes - oppdaterer og returnerer arrangor`() {
		val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
		repository.insertOrUpdate(arrangor)
		val arrangorOppdatert = arrangor.copy(navn = "Bar")

		repository.insertOrUpdate(arrangorOppdatert) shouldBe arrangorOppdatert
		repository.get(arrangor.id) shouldBe arrangorOppdatert
	}

	@Test
	fun `get(uuid) - arrangor finnes ikke - returnerer null`() {
		repository.get(UUID.randomUUID()) shouldBe null
	}

	@Test
	fun `get(uuid) - arrangor finnes - returnerer arrangor`() {
		val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
		repository.insertOrUpdate(arrangor)
		repository.get(arrangor.id) shouldBe arrangor
	}

	@Test
	fun `get(string) - arrangor finnes ikke - returnerer null`() {
		repository.get("Foobar") shouldBe null
	}

	@Test
	fun `get(string) - arrangor finnes - returnerer arrangor`() {
		val arrangor = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
		repository.insertOrUpdate(arrangor)
		repository.get(arrangor.organisasjonsnummer) shouldBe arrangor
	}

	@Test
	fun `getArrangorerMedIder - arrangorer finnes ikke - returnerer tom liste`() {
		repository.getArrangorerMedIder(listOf(UUID.randomUUID())) shouldBe emptyList()
	}

	@Test
	fun `getArrangorerMedIder - arrangorer finnes - returnerer liste`() {
		val arrangor1 = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
		val arrangor2 = ArrangorDbo(UUID.randomUUID(), "Bar", randomOrgnr(), null)
		repository.insertOrUpdate(arrangor1)
		repository.insertOrUpdate(arrangor2)

		repository.getArrangorerMedIder(listOf(arrangor1.id, arrangor2.id)) shouldBe listOf(arrangor1, arrangor2)
	}

	@Test
	fun `getArrangorerMedOrgnumre - arrangorer finnes ikke - returnerer tom liste`() {
		repository.getArrangorerMedOrgnumre(listOf("Foobar")) shouldBe emptyList()
	}

	@Test
	fun `getArrangorerMedOrgnumre - arrangorer finnes - returnerer liste`() {
		val arrangor1 = ArrangorDbo(UUID.randomUUID(), "Foo", randomOrgnr(), null)
		val arrangor2 = ArrangorDbo(UUID.randomUUID(), "Bar", randomOrgnr(), null)
		repository.insertOrUpdate(arrangor1)
		repository.insertOrUpdate(arrangor2)

		repository.getArrangorerMedOrgnumre(
			listOf(
				arrangor1.organisasjonsnummer,
				arrangor2.organisasjonsnummer,
			),
		) shouldBe listOf(arrangor1, arrangor2)
	}

	private fun randomOrgnr() = (900_000_000..999_999_998).random().toString()
}
