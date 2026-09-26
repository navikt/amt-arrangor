package no.nav.arrangor.ansatt

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

/**
 * 🔴 RØD SONE (docs/ansatt-arrangor-dual-write.md):
 * `@Transactional` har ingen presedens i denne kodebasen fra før denne migreringen, så det kan
 * IKKE antas at [AnsattArrangorSyncService] faktisk ruller tilbake jsonb-skrivingen når
 * tabell-skrivingen feiler bare fordi annotasjonen er til stede. Proxy-basert AOP kan svikte
 * stille ved f.eks. selvkall internt i klassen, `final`-klasser/metoder uten
 * `kotlin("plugin.spring")`, eller manglende transaksjonsmanager-bean. Denne testen beviser at
 * det faktisk fungerer i akkurat denne konteksten (full Spring-kontekst, ekte Postgres via
 * testcontainer) — ikke bare at koden kompilerer.
 */
class AnsattArrangorSyncServiceTest(
    private val ansattArrangorSyncService: AnsattArrangorSyncService,
    private val ansattRepository: AnsattRepository,
    @MockkBean private val ansattArrangorRepository: AnsattArrangorRepository,
) : IntegrationTest() {
    @Nested
    inner class OpprettAnsatt {
        @Test
        fun `lagrer jsonb og speiler arrangorer`() {
            // Arrange
            val arrangor = testDatabase.insertArrangor()
            val onsketTilstand = listOf(
                testDatabase.ansattArrangorDbo(arrangorId = arrangor.id),
            )
            val nyAnsatt = testDatabase.ansatt(arrangorer = onsketTilstand)
            val speiletTilstand = slot<List<ArrangorDbo>>()
            every {
                ansattArrangorRepository.replaceForAnsatt(nyAnsatt.id, capture(speiletTilstand))
            } just Runs

            // Act
            val lagretAnsatt = ansattArrangorSyncService.opprettAnsatt(nyAnsatt)

            // Assert
            val ansattFraDatabase = ansattRepository.get(nyAnsatt.id).shouldNotBeNull()
            val lagretArrangor = lagretAnsatt.arrangorer.single()
            val onsketArrangor = onsketTilstand.single()
            lagretArrangor.arrangorId shouldBe arrangor.id
            lagretAnsatt.arrangorer shouldBe ansattFraDatabase.arrangorer
            lagretAnsatt.arrangorer shouldBe speiletTilstand.captured
            lagretArrangor.roller.single().rolle shouldBe onsketArrangor.roller.single().rolle
            lagretArrangor.roller
                .single()
                .gyldigFra
                .toInstant() shouldBe
                onsketArrangor.roller
                    .single()
                    .gyldigFra
                    .toInstant()
            lagretArrangor.koordinator.single().deltakerlisteId shouldBe
                onsketArrangor.koordinator.single().deltakerlisteId
            lagretArrangor.koordinator
                .single()
                .gyldigFra
                .toInstant() shouldBe
                onsketArrangor.koordinator
                    .single()
                    .gyldigFra
                    .toInstant()

            verify(exactly = 1) {
                ansattArrangorRepository.replaceForAnsatt(
                    ansattId = nyAnsatt.id,
                    arrangorer = speiletTilstand.captured,
                )
            }
        }

        @Test
        fun `tabellfeil ruller tilbake jsonb-endringen`() {
            // Arrange
            every {
                ansattArrangorRepository.replaceForAnsatt(any(), any())
            } throws RuntimeException("Simulert feil i tabell-skriving")

            val nyAnsatt = testDatabase.ansatt(
                arrangorer = listOf(
                    ArrangorDbo(
                        arrangorId = UUID.randomUUID(),
                        roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                        veileder = emptyList(),
                        koordinator = emptyList(),
                    ),
                ),
            )

            // Act
            shouldThrow<RuntimeException> {
                ansattArrangorSyncService.opprettAnsatt(nyAnsatt)
            }

            // Assert
            ansattRepository.get(nyAnsatt.id) shouldBe null
        }
    }

    @Nested
    inner class InsertVeileder {
        @Test
        fun `tabellfeil ruller tilbake jsonb-endringen`() {
            val arrangor = testDatabase.insertArrangor()
            val ansatt = testDatabase.insertAnsatt(
                arrangorer = listOf(
                    ArrangorDbo(
                        arrangorId = arrangor.id,
                        roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
                        veileder = emptyList(),
                        koordinator = emptyList(),
                    ),
                ),
            )
            val nyVeileder = VeilederDeltakerDbo(UUID.randomUUID(), VeilederType.VEILEDER)
            val oppdatertAnsatt = ansatt.copy(
                arrangorer = listOf(ansatt.arrangorer.single().copy(veileder = listOf(nyVeileder))),
            )
            every {
                ansattArrangorRepository.insertVeileder(ansatt.id, arrangor.id, nyVeileder)
            } throws RuntimeException("Simulert feil i tabell-skriving")

            shouldThrow<RuntimeException> {
                ansattArrangorSyncService.insertVeileder(oppdatertAnsatt, arrangor.id, nyVeileder)
            }

            verify(exactly = 1) {
                ansattArrangorRepository.insertVeileder(ansatt.id, arrangor.id, nyVeileder)
            }
            ansattRepository
                .get(ansatt.id)
                .shouldNotBeNull()
                .arrangorer
                .single()
                .veileder
                .shouldBeEmpty()
        }
    }

    @Nested
    inner class DeaktiverVeiledereForDeltaker {
        @Test
        fun `oppdaterer jsonb og speiler tidspunktet`() {
            // Arrange
            val deltakerId = UUID.randomUUID()
            val ansatt = insertAnsattMedVeileder(deltakerId)
            val deaktiveringstidspunkt = ZonedDateTime.parse("2026-09-25T10:15:30Z")
            every {
                ansattArrangorRepository.deaktiverVeiledereForDeltaker(deltakerId, deaktiveringstidspunkt)
            } returns listOf(ansatt.id)

            // Act
            val endredeAnsatte = ansattArrangorSyncService.deaktiverVeiledereForDeltaker(
                deltakerId,
                deaktiveringstidspunkt,
            )

            // Assert
            val endretVeilederGyldigTil = endredeAnsatte
                .ansatteEndretIJsonb
                .single()
                .arrangorer
                .single()
                .veileder
                .single()
                .gyldigTil
                .shouldNotBeNull()

            val ansattFraDatabase = ansattRepository.get(ansatt.id).shouldNotBeNull()
            val lagretVeilederGyldigTil = ansattFraDatabase
                .arrangorer
                .single()
                .veileder
                .single()
                .gyldigTil
                .shouldNotBeNull()

            endredeAnsatte.ansatteEndretIJsonb.map { it.id } shouldBe listOf(ansatt.id)
            endredeAnsatte.ansattIderEndretINormaliserteTabeller shouldBe listOf(ansatt.id)
            endretVeilederGyldigTil.toInstant() shouldBe deaktiveringstidspunkt.toInstant()
            lagretVeilederGyldigTil.toInstant() shouldBe deaktiveringstidspunkt.toInstant()

            verify(exactly = 1) {
                ansattArrangorRepository.deaktiverVeiledereForDeltaker(deltakerId, deaktiveringstidspunkt)
            }
        }

        @Test
        fun `tabellfeil ruller tilbake jsonb-endringen`() {
            // Arrange
            val deltakerId = UUID.randomUUID()
            val ansatt = insertAnsattMedVeileder(deltakerId)
            val deaktiveringstidspunkt = ZonedDateTime.parse("2026-09-25T10:15:30Z")
            every {
                ansattArrangorRepository.deaktiverVeiledereForDeltaker(deltakerId, deaktiveringstidspunkt)
            } throws RuntimeException("Simulert feil i tabell-skriving")

            // Act
            shouldThrow<RuntimeException> {
                ansattArrangorSyncService.deaktiverVeiledereForDeltaker(deltakerId, deaktiveringstidspunkt)
            }

            // Assert
            val ansattFraDatabase = ansattRepository.get(ansatt.id).shouldNotBeNull()
            ansattFraDatabase
                .arrangorer
                .single()
                .veileder
                .single()
                .gyldigTil shouldBe null
            ansattFraDatabase.modifiedAt shouldBe ansatt.modifiedAt
        }
    }

    @Nested
    inner class MaybeReaktiverVeiledereForDeltaker {
        @Test
        fun `nullstiller jsonb og speiler terskelen`() {
            // Arrange
            val deltakerId = UUID.randomUUID()
            val gyldigTil = ZonedDateTime.parse("2030-01-01T00:00:00Z")
            val terskel = ZonedDateTime.parse("2029-01-01T00:00:00Z")
            val ansatt = insertAnsattMedVeileder(deltakerId, gyldigTil)

            every {
                ansattArrangorRepository.maybeReaktiverVeiledereForDeltaker(deltakerId, terskel)
            } returns listOf(ansatt.id)

            // Act
            val endredeAnsatte = ansattArrangorSyncService.maybeReaktiverVeiledereForDeltaker(deltakerId, terskel)

            // Assert
            val ansattFraDatabase = ansattRepository.get(ansatt.id).shouldNotBeNull()
            endredeAnsatte.ansatteEndretIJsonb.map { it.id } shouldBe listOf(ansatt.id)
            endredeAnsatte.ansattIderEndretINormaliserteTabeller shouldBe listOf(ansatt.id)
            endredeAnsatte
                .ansatteEndretIJsonb
                .single()
                .arrangorer
                .single()
                .veileder
                .single()
                .gyldigTil shouldBe null
            ansattFraDatabase
                .arrangorer
                .single()
                .veileder
                .single()
                .gyldigTil shouldBe null
            verify(exactly = 1) {
                ansattArrangorRepository.maybeReaktiverVeiledereForDeltaker(deltakerId, terskel)
            }
        }

        @Test
        fun `tabellfeil ruller tilbake jsonb-endringen`() {
            // Arrange
            val deltakerId = UUID.randomUUID()
            val gyldigTil = ZonedDateTime.parse("2030-01-01T00:00:00Z")
            val terskel = ZonedDateTime.parse("2029-01-01T00:00:00Z")
            val ansatt = insertAnsattMedVeileder(deltakerId, gyldigTil)
            every {
                ansattArrangorRepository.maybeReaktiverVeiledereForDeltaker(deltakerId, terskel)
            } throws RuntimeException("Simulert feil i tabell-skriving")

            // Act
            shouldThrow<RuntimeException> {
                ansattArrangorSyncService.maybeReaktiverVeiledereForDeltaker(deltakerId, terskel)
            }

            // Assert
            val ansattFraDatabase = ansattRepository.get(ansatt.id).shouldNotBeNull()
            val gyldigTilEtterFeil = ansattFraDatabase
                .arrangorer
                .single()
                .veileder
                .single()
                .gyldigTil
                .shouldNotBeNull()
            gyldigTilEtterFeil.toInstant() shouldBe gyldigTil.toInstant()
            ansattFraDatabase.modifiedAt shouldBe ansatt.modifiedAt
        }
    }

    private fun insertAnsattMedVeileder(
        deltakerId: UUID,
        gyldigTil: ZonedDateTime? = null,
    ) = testDatabase.insertAnsatt(
        arrangorer = listOf(
            ArrangorDbo(
                arrangorId = testDatabase.insertArrangor().id,
                roller = emptyList(),
                veileder = listOf(
                    VeilederDeltakerDbo(
                        deltakerId = deltakerId,
                        veilederType = VeilederType.VEILEDER,
                        gyldigFra = ZonedDateTime.parse("2025-01-01T00:00:00Z"),
                        gyldigTil = gyldigTil,
                    ),
                ),
                koordinator = emptyList(),
            ),
        ),
    )
}
