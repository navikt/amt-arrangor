package no.nav.arrangor.ansatt.repository

import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.shouldBe
import no.nav.arrangor.RepositoryTestBase
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class AnsattRepositoryTest(
	private val ansattRepository: AnsattRepository,
) : RepositoryTestBase() {
	@Test
	fun `insertOrUpdate - not exists - adds to database`() {
		val ansattId = UUID.randomUUID()
		val arrangorId = UUID.randomUUID()
		val deltakerId = UUID.randomUUID()
		val ansatt =
			AnsattDbo(
				id = ansattId,
				personident = "123456",
				personId = UUID.randomUUID(),
				fornavn = "Test",
				mellomnavn = "Mellom",
				etternavn = "Testersen",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorId,
							roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
							veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
							koordinator = emptyList(),
						),
					),
			)

		val inserted = ansattRepository.insertOrUpdate(ansatt)

		inserted.id shouldBe ansattId
		inserted.personident shouldBe ansatt.personident
		inserted.fornavn shouldBe ansatt.fornavn
		inserted.mellomnavn shouldBe ansatt.mellomnavn
		inserted.etternavn shouldBe ansatt.etternavn
		inserted.arrangorer.size shouldBe 1
		val arrangor = inserted.arrangorer[0]
		arrangor.arrangorId shouldBe arrangorId
		arrangor.roller.size shouldBe 1
		arrangor.roller[0].rolle shouldBe AnsattRolle.VEILEDER
		arrangor.roller[0].erGyldig() shouldBe true
		arrangor.veileder[0].deltakerId shouldBe deltakerId
		arrangor.veileder[0].veilederType shouldBe VeilederType.MEDVEILEDER
		arrangor.veileder[0].erGyldig() shouldBe true
		arrangor.koordinator.size shouldBe 0
	}

	@Test
	fun `insertOrUpdate - exists - updates`() {
		val ansattId = UUID.randomUUID()
		val arrangorId = UUID.randomUUID()
		val deltakerId = UUID.randomUUID()
		val oldAnsatt =
			AnsattDbo(
				id = ansattId,
				personident = "123456",
				personId = UUID.randomUUID(),
				fornavn = "Test",
				mellomnavn = "Mellom",
				etternavn = "Testersen",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorId,
							roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
							veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
							koordinator = emptyList(),
						),
					),
			)
		ansattRepository.insertOrUpdate(oldAnsatt)
		val oppdatertAnsatt =
			AnsattDbo(
				id = ansattId,
				personident = "123456",
				personId = oldAnsatt.personId,
				fornavn = "Test2",
				mellomnavn = null,
				etternavn = "Testersen2",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorId,
							roller = listOf(RolleDbo(AnsattRolle.VEILEDER), RolleDbo(AnsattRolle.KOORDINATOR)),
							veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
							koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
						),
					),
			)

		val new = ansattRepository.insertOrUpdate(oppdatertAnsatt)

		new.id shouldBe ansattId
		new.personident shouldBe oldAnsatt.personident
		new.fornavn shouldBe "Test2"
		new.mellomnavn shouldBe null
		new.etternavn shouldBe "Testersen2"
		val arrangor = new.arrangorer[0]
		arrangor.arrangorId shouldBe arrangorId
		arrangor.roller.size shouldBe 2
		arrangor.veileder[0].deltakerId shouldBe deltakerId
		arrangor.koordinator.size shouldBe 1
	}

	@Test
	fun `get(UUID) - not exists - returns null`() {
		ansattRepository.get(UUID.randomUUID()) shouldBe null
	}

	@Test
	fun `get(UUID) - exists - returns Ansatt`() {
		val ansattId = UUID.randomUUID()
		val arrangorId = UUID.randomUUID()
		val deltakerId = UUID.randomUUID()
		val stored =
			AnsattDbo(
				id = ansattId,
				personident = "123456",
				personId = UUID.randomUUID(),
				fornavn = "Test",
				mellomnavn = "Mellom",
				etternavn = "Testersen",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorId,
							roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
							veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
							koordinator = emptyList(),
						),
					),
			).let { ansattRepository.insertOrUpdate(it) }

		ansattRepository.get(stored.id) shouldBe stored
	}

	@Test
	fun `get(personident) - not exists - returns null`() {
		ansattRepository.get(UUID.randomUUID().toString()) shouldBe null
	}

	@Test
	fun `get(personident) - exists - returns Ansatt`() {
		val ansattId = UUID.randomUUID()
		val arrangorId = UUID.randomUUID()
		val deltakerId = UUID.randomUUID()
		val stored =
			AnsattDbo(
				id = ansattId,
				personident = "123456",
				personId = UUID.randomUUID(),
				fornavn = "Test",
				mellomnavn = "Mellom",
				etternavn = "Testersen",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorId,
							roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
							veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
							koordinator = emptyList(),
						),
					),
			).let { ansattRepository.insertOrUpdate(it) }

		ansattRepository.get(stored.personident) shouldBe stored
	}

	@Test
	fun `getByPersonId - exists - returns Ansatt`() {
		val ansattId = UUID.randomUUID()
		val arrangorId = UUID.randomUUID()
		val deltakerId = UUID.randomUUID()
		val stored =
			AnsattDbo(
				id = ansattId,
				personident = "123456",
				personId = UUID.randomUUID(),
				fornavn = "Test",
				mellomnavn = "Mellom",
				etternavn = "Testersen",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorId,
							roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
							veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
							koordinator = emptyList(),
						),
					),
			).let { ansattRepository.insertOrUpdate(it) }

		ansattRepository.getByPersonId(stored.personId) shouldBe stored
	}

	@Test
	fun `getByPersonId - not exists - returns null`() {
		ansattRepository.getByPersonId(UUID.randomUUID()) shouldBe null
	}

	@Test
	fun `getToSynchronize - Returns only values to synchronize`() {
		testDatabase.ansatt().let { ansattRepository.insertOrUpdate(it) }
		val two = testDatabase.ansatt(lastSynchronized = LocalDateTime.now().minusDays(8)).let { ansattRepository.insertOrUpdate(it) }

		val returned = ansattRepository.getToSynchronize(5, LocalDateTime.now().minusDays(7))

		returned.size shouldBe 1
		returned[0] shouldBe two
	}

	@Test
	fun `getToSynchronize - returns max limit and oldest ordered`() {
		testDatabase.ansatt().let { ansattRepository.insertOrUpdate(it) }
		val two = testDatabase.ansatt(lastSynchronized = LocalDateTime.now().minusDays(2)).let { ansattRepository.insertOrUpdate(it) }
		val three = testDatabase.ansatt(lastSynchronized = LocalDateTime.now().minusDays(1)).let { ansattRepository.insertOrUpdate(it) }

		val returned = ansattRepository.getToSynchronize(2, LocalDateTime.now().plusMinutes(7))

		returned.size shouldBe 2
		returned shouldContainInOrder listOf(two, three)
	}

	@Test
	fun `deaktiverVeiledereForDeltaker - aktive veiledere - deaktiverer alle aktive veiledere for deltaker`() {
		val deltaker1 = UUID.randomUUID()
		val deltaker2 = UUID.randomUUID()
		val arrangor = UUID.randomUUID()

		val ansatt1 =
			testDatabase.ansatt(
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangor,
							listOf(RolleDbo(AnsattRolle.VEILEDER, ZonedDateTime.now().minusDays(7), null)),
							listOf(
								VeilederDeltakerDbo(deltaker1, VeilederType.VEILEDER),
								VeilederDeltakerDbo(deltaker2, VeilederType.MEDVEILEDER),
							),
							emptyList(),
						),
					),
			)
		val ansatt2 =
			testDatabase.ansatt(
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangor,
							listOf(RolleDbo(AnsattRolle.VEILEDER, ZonedDateTime.now().minusDays(7), null)),
							listOf(
								VeilederDeltakerDbo(deltaker1, VeilederType.MEDVEILEDER),
								VeilederDeltakerDbo(deltaker2, VeilederType.VEILEDER),
							),
							emptyList(),
						),
					),
			)
		ansattRepository.insertOrUpdate(ansatt1)
		ansattRepository.insertOrUpdate(ansatt2)

		val deaktiveringsdato = ZonedDateTime.now().plusDays(1)

		val oppdaterteAnsatte = ansattRepository.deaktiverVeiledereForDeltaker(deltaker1, deaktiveringsdato)
		oppdaterteAnsatte shouldHaveSize 2

		val oppdatertAnsatt1 = oppdaterteAnsatte.first { it.id == ansatt1.id }
		oppdatertAnsatt1.arrangorer.forEach { arr ->
			arr.veileder
				.first { it.deltakerId == deltaker1 }
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(1), deaktiveringsdato)

			arr.veileder.first { it.deltakerId == deltaker2 }.gyldigTil shouldBe null
		}

		val oppdatertAnsatt2 = oppdaterteAnsatte.first { it.id == ansatt2.id }
		oppdatertAnsatt2.arrangorer.forEach { arr ->
			arr.veileder
				.first { it.deltakerId == deltaker1 }
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(1), deaktiveringsdato)
			arr.veileder.first { it.deltakerId == deltaker2 }.gyldigTil shouldBe null
		}
	}

	@Test
	fun `getAnsatteHosArrangor - flere ansatte og arrangorer - returnerer ansatte med roller hos arrangor`() {
		val arrangor1 = testDatabase.ansattArrangorDbo()
		val arrangor2 = testDatabase.ansattArrangorDbo()

		val ansatt1 = testDatabase.insertAnsatt(arrangorer = listOf(arrangor1, arrangor2))
		val ansatt2 = testDatabase.insertAnsatt(arrangorer = listOf(arrangor1))
		val ansatt3 = testDatabase.insertAnsatt(arrangorer = listOf(arrangor2))
		val ansatt4 = testDatabase.insertAnsatt(arrangorer = listOf())

		val ansatteArrangor1 = ansattRepository.getAnsatteHosArrangor(arrangor1.arrangorId)
		ansatteArrangor1.any { it == ansatt1 } shouldBe true
		ansatteArrangor1.any { it == ansatt2 } shouldBe true
		ansatteArrangor1.any { it == ansatt3 } shouldBe false
		ansatteArrangor1.any { it == ansatt4 } shouldBe false

		val ansatteArrangor2 = ansattRepository.getAnsatteHosArrangor(arrangor2.arrangorId)
		ansatteArrangor2.any { it == ansatt1 } shouldBe true
		ansatteArrangor2.any { it == ansatt2 } shouldBe false
		ansatteArrangor2.any { it == ansatt3 } shouldBe true
		ansatteArrangor2.any { it == ansatt4 } shouldBe false
	}
}
