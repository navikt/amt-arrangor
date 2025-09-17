package no.nav.arrangor.ansatt.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.arrangor.RepositoryTestBase
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.utils.shouldBeCloseTo
import no.nav.arrangor.utils.shouldBeCloseToNow
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class AnsattRepositoryTest(
	private val ansattRepository: AnsattRepository,
) : RepositoryTestBase() {
	@Nested
	inner class InsertOrUpdate {
		@Test
		fun `insertOrUpdate - not exists - adds to database`() {
			val inserted = ansattRepository.insertOrUpdate(ansattInTest)

			assertSoftly(inserted) {
				id shouldBe ansattInTest.id
				personident shouldBe ansattInTest.personident
				fornavn shouldBe ansattInTest.fornavn
				mellomnavn shouldBe ansattInTest.mellomnavn
				etternavn shouldBe ansattInTest.etternavn
				modifiedAt.shouldBeCloseToNow()
				lastSynchronized.shouldBeCloseToNow()

				arrangorer.size shouldBe 1
				assertSoftly(inserted.arrangorer.first()) {
					arrangorId shouldBe arrangorId

					roller.size shouldBe 1
					assertSoftly(roller.first()) {
						rolle shouldBe AnsattRolle.VEILEDER
						erGyldig() shouldBe true
					}

					veileder.size shouldBe 1
					assertSoftly(veileder.first()) {
						deltakerId shouldBe deltakerId
						veilederType shouldBe VeilederType.MEDVEILEDER
						erGyldig() shouldBe true
					}

					koordinator.shouldBeEmpty()
				}
			}
		}

		@Test
		fun `insertOrUpdate - exists - updates`() {
			ansattRepository.insertOrUpdate(ansattInTest)

			val oppdatertAnsatt = ansattInTest.copy(
				fornavn = "Test2",
				mellomnavn = null,
				etternavn = "Testersen2",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorId,
							roller = listOf(
								RolleDbo(AnsattRolle.VEILEDER),
								RolleDbo(AnsattRolle.KOORDINATOR),
							),
							veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.MEDVEILEDER)),
							koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
						),
					),
			)

			val updatedAnsattInDb = ansattRepository.insertOrUpdate(oppdatertAnsatt)

			assertSoftly(updatedAnsattInDb) {
				id shouldBe ansattInTest.id
				personident shouldBe ansattInTest.personident
				fornavn shouldBe "Test2"
				mellomnavn shouldBe null
				etternavn shouldBe "Testersen2"

				arrangorer.size shouldBe 1
				assertSoftly(arrangorer.first()) {
					arrangorId shouldBe arrangorId
					roller.size shouldBe 2

					veileder.size shouldBe 1
					veileder.first().deltakerId shouldBe deltakerId

					koordinator.size shouldBe 1
				}
			}
		}
	}

	@Nested
	inner class GetAll {
		@Test
		fun `skal returnere tom liste hvis tabell er tom`() {
			ansattRepository.getAll(offset = 0, limit = 100).shouldBeEmpty()
		}

		@Test
		fun `skal returnere liste sortert pa modified_at i stigende rekkefolge`() {
			val tomorrow = LocalDateTime.now().plusDays(1)

			ansattRepository.insertOrUpdate(ansattInTest)
			ansattRepository.insertOrUpdate(
				ansattInTest.copy(
					id = UUID.randomUUID(),
					personId = UUID.randomUUID(),
					personident = "~personIdent2~",
					modifiedAt = tomorrow,
				),
			)
			ansattRepository.insertOrUpdate(
				ansattInTest.copy(
					id = UUID.randomUUID(),
					personId = UUID.randomUUID(),
					personident = "~personIdent3~",
					modifiedAt = LocalDateTime.now().minusDays(1),
				),
			)

			val allAnsatteInDb = ansattRepository.getAll(offset = 0, limit = 100)
			allAnsatteInDb.size shouldBe 3

			val lastAnsatt = allAnsatteInDb.last()
			lastAnsatt.modifiedAt shouldBeCloseTo tomorrow
		}
	}

	@Nested
	inner class GetById {
		@Test
		fun `get(UUID) - not exists - returns null`() {
			ansattRepository.get(UUID.randomUUID()) shouldBe null
		}

		@Test
		fun `get(UUID) - exists - returns Ansatt`() {
			val ansattInDb = ansattRepository.insertOrUpdate(ansattInTest)

			ansattRepository.get(ansattInTest.id) shouldBe ansattInDb
		}
	}

	@Nested
	inner class GetByPersonIdent {
		@Test
		fun `get(personident) - not exists - returns null`() {
			ansattRepository.get(UUID.randomUUID().toString()) shouldBe null
		}

		@Test
		fun `get(personident) - exists - returns Ansatt`() {
			val ansattInDb = ansattRepository.insertOrUpdate(ansattInTest)

			ansattRepository.get(ansattInTest.personident) shouldBe ansattInDb
		}
	}

	@Nested
	inner class GetByPersonId {
		@Test
		fun `getByPersonId - exists - returns Ansatt`() {
			val ansattInDb = ansattRepository.insertOrUpdate(ansattInTest)

			ansattRepository.getByPersonId(ansattInDb.personId) shouldBe ansattInDb
		}

		@Test
		fun `getByPersonId - not exists - returns null`() {
			ansattRepository.getByPersonId(UUID.randomUUID()) shouldBe null
		}
	}

	@Nested
	inner class GetToSynchronize {
		@Test
		fun `getToSynchronize - Returns only values to synchronize`() {
			ansattRepository.insertOrUpdate(testDatabase.ansatt())
			val updatedAnsattInDb = ansattRepository.insertOrUpdate(
				testDatabase.ansatt(
					lastSynchronized = LocalDateTime.now().minusDays(8),
				),
			)

			val returned = ansattRepository.getToSynchronize(5, LocalDateTime.now().minusDays(7))

			returned.size shouldBe 1
			returned.first() shouldBe updatedAnsattInDb
		}

		@Test
		fun `getToSynchronize - returns max limit and oldest ordered`() {
			ansattRepository.insertOrUpdate(testDatabase.ansatt())

			val two = ansattRepository.insertOrUpdate(testDatabase.ansatt(lastSynchronized = LocalDateTime.now().minusDays(2)))
			val three = ansattRepository.insertOrUpdate(testDatabase.ansatt(lastSynchronized = LocalDateTime.now().minusDays(1)))

			val returned = ansattRepository.getToSynchronize(2, LocalDateTime.now().plusMinutes(7))

			returned.size shouldBe 2
			returned shouldContainInOrder listOf(two, three)
		}
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
		oppdatertAnsatt1.arrangorer.forEach { arrangorDbo ->
			val firstVeileder = arrangorDbo.veileder.first { it.deltakerId == deltaker1 }
			assertSoftly(firstVeileder.gyldigTil.shouldNotBeNull()) {
				it.shouldBeCloseTo(deaktiveringsdato)
			}

			val secondVeileder = arrangorDbo.veileder.first { it.deltakerId == deltaker2 }
			secondVeileder.gyldigTil shouldBe null
		}

		val oppdatertAnsatt2 = oppdaterteAnsatte.first { it.id == ansatt2.id }
		oppdatertAnsatt2.arrangorer.forEach { arrangorDbo ->
			val firstVeileder = arrangorDbo.veileder.first { it.deltakerId == deltaker1 }
			assertSoftly(firstVeileder.gyldigTil.shouldNotBeNull()) {
				it.shouldBeCloseTo(deaktiveringsdato)
			}

			val secondVeileder = arrangorDbo.veileder.first { it.deltakerId == deltaker2 }
			secondVeileder.gyldigTil shouldBe null
		}
	}

	@Test
	fun `maybeReaktiverVeiledereForDeltaker - reaktiverer gyldige veiledere for deltaker`() {
		val deltaker1 = UUID.randomUUID()
		val deltaker2 = UUID.randomUUID()
		val arrangor = UUID.randomUUID()

		val ansatt1 =
			testDatabase.ansatt(
				arrangorer = listOf(
					ArrangorDbo(
						arrangorId = arrangor,
						roller = listOf(RolleDbo(AnsattRolle.VEILEDER, ZonedDateTime.now().minusDays(7), null)),
						veileder = listOf(
							VeilederDeltakerDbo(
								deltaker1,
								VeilederType.VEILEDER,
								ZonedDateTime.now().minusDays(2),
								ZonedDateTime.now().plusDays(2),
							),
							VeilederDeltakerDbo(deltaker2, VeilederType.MEDVEILEDER),
						),
						koordinator = emptyList(),
					),
				),
			)

		val ansatt2 =
			testDatabase.ansatt(
				arrangorer = listOf(
					ArrangorDbo(
						arrangorId = arrangor,
						roller = listOf(RolleDbo(AnsattRolle.VEILEDER, ZonedDateTime.now().minusDays(7), null)),
						veileder = listOf(
							VeilederDeltakerDbo(
								deltaker1,
								VeilederType.MEDVEILEDER,
								ZonedDateTime.now().minusDays(3),
								ZonedDateTime.now().plusDays(3),
							),
							VeilederDeltakerDbo(deltaker2, VeilederType.VEILEDER),
						),
						koordinator = emptyList(),
					),
				),
			)

		ansattRepository.insertOrUpdate(ansatt1)
		ansattRepository.insertOrUpdate(ansatt2)

		// Utfør reaktivering
		val oppdaterteAnsatte = ansattRepository.maybeReaktiverVeiledereForDeltaker(deltaker1)

		oppdaterteAnsatte shouldHaveSize 2

		// Sjekk at deltaker1 er reaktivert (gyldigTil = null) og deltaker2 uendret
		oppdaterteAnsatte.forEach { ansatt ->
			ansatt.arrangorer.forEach { arrangorDbo ->
				val reaktivertVeileder = arrangorDbo.veileder.first { it.deltakerId == deltaker1 }
				reaktivertVeileder.gyldigTil shouldBe null

				val uendretVeileder = arrangorDbo.veileder.first { it.deltakerId == deltaker2 }
				uendretVeileder.gyldigTil.shouldBeNull()
			}
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

	companion object {
		private val arrangorId = UUID.randomUUID()
		private val deltakerId = UUID.randomUUID()

		private val ansattInTest = AnsattDbo(
			id = UUID.randomUUID(),
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
						veileder = listOf(
							VeilederDeltakerDbo(
								deltakerId = deltakerId,
								veilederType = VeilederType.MEDVEILEDER,
							),
						),
						koordinator = emptyList(),
					),
				),
		)
	}
}
