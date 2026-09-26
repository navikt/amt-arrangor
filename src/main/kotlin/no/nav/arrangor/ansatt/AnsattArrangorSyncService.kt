package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Koordinerer dual-write mellom `ansatt.arrangorer` (jsonb) og de normaliserte tabellene
 * `ansatt_arrangor`(_rolle|_veileder|_koordinator). Hver forretningsoperasjon sendes eksplisitt
 * til begge representasjonene, slik at de normaliserte tabellene ikke bygges opp igjen fra jsonb.
 * All skriving skjer i én transaksjon slik at representasjonene ikke drifter pga. delvis feil
 * (ingen `@Transactional`-presedens fantes i kodebasen fra før — se
 * docs/ansatt-arrangor-dual-write.md).
 *
 * Denne klassen tar IKKE forretningsbeslutninger — den mottar ferdig beregnede operasjoner fra
 * [AnsattService]/[AnsattRolleService] og skriver dem begge steder.
 * [ZonedDateTime] for `gyldigTil` MÅ være beregnet én gang av kalleren og sendes uendret inn hit
 * (se §8.4) — denne klassen kaller aldri selv `ZonedDateTime.now()` for å unngå å gjenskape de
 * historiske mikrosekund-nær-duplikatene V14 måtte deduplisere bort.
 */
@Service
class AnsattArrangorSyncService(
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
) {
    /** Full initialisering brukes bare når en ny ansatt opprettes etter Flyway-backfillen. */
    @Transactional
    fun opprettAnsatt(ansatt: AnsattDbo): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.replaceForAnsatt(lagretAnsatt.id, lagretAnsatt.arrangorer)
        return lagretAnsatt
    }

    @Transactional
    fun oppdaterRoller(oppdatering: AnsattRolleoppdatering): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(oppdatering.data)
        oppdatering.nyeRoller.forEach {
            ansattArrangorRepository.insertRolle(lagretAnsatt.id, it.arrangorId, it.rolle)
        }
        oppdatering.deaktiverteTilganger.forEach { deaktiverte ->
            ansattArrangorRepository.deaktiverRolle(
                lagretAnsatt.id,
                deaktiverte.arrangorId,
                deaktiverte.rolle,
            )
            deaktiverte.veiledere.forEach {
                ansattArrangorRepository.deaktiverVeileder(lagretAnsatt.id, deaktiverte.arrangorId, it)
            }
            deaktiverte.koordinatorer.forEach {
                ansattArrangorRepository.deaktiverKoordinator(lagretAnsatt.id, deaktiverte.arrangorId, it)
            }
        }
        return lagretAnsatt
    }

    @Transactional
    fun insertVeileder(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        veileder: VeilederDeltakerDbo,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.insertVeileder(lagretAnsatt.id, arrangorId, veileder)
        return lagretAnsatt
    }

    @Transactional
    fun deaktiverVeiledere(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        veiledere: List<VeilederDeltakerDbo>,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        veiledere.forEach { ansattArrangorRepository.deaktiverVeileder(lagretAnsatt.id, arrangorId, it) }
        return lagretAnsatt
    }

    @Transactional
    fun insertKoordinator(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        koordinator: KoordinatorsDeltakerlisteDbo,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.insertKoordinator(lagretAnsatt.id, arrangorId, koordinator)
        return lagretAnsatt
    }

    @Transactional
    fun deaktiverKoordinator(
        ansatt: AnsattDbo,
        arrangorId: UUID,
        koordinator: KoordinatorsDeltakerlisteDbo,
    ): AnsattDbo {
        val lagretAnsatt = ansattRepository.insertOrUpdate(ansatt)
        ansattArrangorRepository.deaktiverKoordinator(lagretAnsatt.id, arrangorId, koordinator)
        return lagretAnsatt
    }

    /**
     * Speiler [AnsattRepository.deaktiverVeiledereForDeltaker] mot [AnsattArrangorRepository]
     * for samme [deltakerId]. [deaktiveringsdato] må være beregnet én gang av kalleren
     * — brukes uendret for begge skrivinger, slik at jsonb og ny tabell aldri kan få avvikende
     * `gyldigTil` for samme logiske hendelse.
     */
    @Transactional
    fun deaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        deaktiveringsdato: ZonedDateTime,
    ): EndredeAnsatte {
        val ansatteEndretIJsonb = ansattRepository.deaktiverVeiledereForDeltaker(deltakerId, deaktiveringsdato)
        val ansattIderEndretINormaliserteTabeller =
            ansattArrangorRepository.deaktiverVeiledereForDeltaker(deltakerId, deaktiveringsdato)
        return EndredeAnsatte(ansatteEndretIJsonb, ansattIderEndretINormaliserteTabeller)
    }

    /**
     * Speiler [AnsattRepository.maybeReaktiverVeiledereForDeltaker] mot [AnsattArrangorRepository]
     * for samme [deltakerId]. [terskel] er grensen for hvilke rader som reaktiveres
     * (`gyldig_til > terskel`), og må — i likhet med [deaktiverVeiledereForDeltaker] — være ett
     * delt tidspunkt beregnet én gang av kalleren, ikke to separate `ZonedDateTime.now()`-kall.
     */
    @Transactional
    fun maybeReaktiverVeiledereForDeltaker(
        deltakerId: UUID,
        terskel: ZonedDateTime,
    ): EndredeAnsatte {
        val ansatteEndretIJsonb = ansattRepository.maybeReaktiverVeiledereForDeltaker(deltakerId, terskel)
        val ansattIderEndretINormaliserteTabeller =
            ansattArrangorRepository.maybeReaktiverVeiledereForDeltaker(deltakerId, terskel)
        return EndredeAnsatte(ansatteEndretIJsonb, ansattIderEndretINormaliserteTabeller)
    }
}

data class EndredeAnsatte(
    val ansatteEndretIJsonb: List<AnsattDbo>,
    val ansattIderEndretINormaliserteTabeller: List<UUID>,
)
