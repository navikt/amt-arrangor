package no.nav.arrangor.ingest

import no.nav.arrangor.ansatt.AnsattRolleService
import no.nav.arrangor.ansatt.repositories.AnsattRepository
import no.nav.arrangor.ansatt.repositories.KoordinatorDeltakerlisteRepository
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.ansatt.repositories.VeilederDeltakerRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.defaultVirksomhet
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.domain.Veileder
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.dto.ArrangorDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IngestService(
    private val arrangorService: ArrangorService,
    private val arrangorRepository: ArrangorRepository,
    private val ansattRepository: AnsattRepository,
    private val rolleRepository: RolleRepository,
    private val rolleService: AnsattRolleService,
    private val koordinatorDeltakerlisteRepository: KoordinatorDeltakerlisteRepository,
    private val veilederDeltakerRepository: VeilederDeltakerRepository,
    private val enhetsregisterClient: EnhetsregisterClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun handleArrangor(arrangor: ArrangorDto) {
        var overordnetArrangor: Arrangor? = null

        if (arrangor.overordnetArrangorId != null) {
            val hentet = enhetsregisterClient.hentVirksomhet(arrangor.organisasjonsnummer).getOrElse {
                logger.warn("Virksomhet med organisasjonsnummer ${arrangor.organisasjonsnummer} finnes ikke, legger inn default")
                defaultVirksomhet()
            }

            if (hentet.organisasjonsnummer != "999999999") {
                val overordnetVirksomhet =
                    enhetsregisterClient.hentVirksomhet(hentet.overordnetEnhetOrganisasjonsnummer!!).getOrElse {
                        logger.warn("Virksomhet med organisasjonsnummer ${hentet.overordnetEnhetOrganisasjonsnummer} finnes ikke, legger inn default")
                        defaultVirksomhet()
                    }
                arrangorRepository.insertOrUpdate(
                    ArrangorRepository.ArrangorDbo(
                        id = arrangor.overordnetArrangorId,
                        navn = overordnetVirksomhet.navn,
                        organisasjonsnummer = overordnetVirksomhet.organisasjonsnummer,
                        overordnetArrangorId = null
                    )
                )
                overordnetArrangor = arrangorService.get(arrangor.overordnetArrangorId)
            }
        }

        val inserted = arrangorRepository.insertOrUpdate(
            ArrangorRepository.ArrangorDbo(
                id = arrangor.id,
                navn = arrangor.navn,
                organisasjonsnummer = arrangor.organisasjonsnummer,
                overordnetArrangorId = overordnetArrangor?.id
            )
        )

        arrangorService.addDeltakerlister(inserted.id, arrangor.deltakerlister.toSet())
        logger.info("Håndterte arrangør med id ${arrangor.id}")
    }

    fun handleAnsatt(ansatt: Ansatt) {
        ansattRepository.insertOrUpdate(
            AnsattRepository.AnsattDbo(
                id = ansatt.id,
                personId = UUID.randomUUID(),
                personident = ansatt.personalia.personident,
                fornavn = ansatt.personalia.navn.fornavn,
                mellomnavn = ansatt.personalia.navn.mellomnavn,
                etternavn = ansatt.personalia.navn.etternavn
            )
        )

        ansatt.arrangorer.forEach { arrangor ->
            val organisasjonsnummer = arrangorRepository.getOrganiasjonsnummerForId(arrangor.arrangorId)

            if (organisasjonsnummer == null) {
                logger.warn("arrangør ${arrangor.arrangorId} er ikke lagret enda")
                return
            }

            val gamleRoller = rolleRepository.getAktiveRoller(ansatt.id).map {
                AnsattRolleService.OrgRolle(
                    id = it.id,
                    organisasjonsnummer = it.organisasjonsnummer,
                    rolle = it.rolle
                )
            }

            val nyeRoller = arrangor.roller.map {
                AnsattRolleService.OrgRolle(
                    id = -1,
                    organisasjonsnummer = organisasjonsnummer,
                    rolle = it
                )
            }

            rolleService.oppdaterRoller(ansatt.id, gamleRoller, nyeRoller)
            oppdaterKoordinatortilganger(ansatt.id, arrangor.koordinator)
            oppdaterVeiledertilganger(ansatt.id, arrangor.veileder)
        }

        logger.info("Konsumerte ansatt med id ${ansatt.id}")
    }

    fun oppdaterKoordinatortilganger(ansattId: UUID, deltakerlisteIds: List<UUID>) {
        val nyeDeltakerlisteIds = deltakerlisteIds.map { KoordinatorDeltakerHolder(-1, it) }

        val gamleDeltakerlisteIds = koordinatorDeltakerlisteRepository.getAktive(ansattId).map {
            KoordinatorDeltakerHolder(
                id = it.id,
                deltakerlisteId = it.deltakerlisteId
            )
        }

        val skalSlettes = gamleDeltakerlisteIds.filter { !nyeDeltakerlisteIds.contains(it) }
        val skalLeggesTil = nyeDeltakerlisteIds.filter { !gamleDeltakerlisteIds.contains(it) }

        koordinatorDeltakerlisteRepository.deaktiverKoordinatorDeltakerliste(skalSlettes.map { it.id })
        koordinatorDeltakerlisteRepository.leggTilKoordinatorDeltakerlister(
            ansattId,
            skalLeggesTil.map { it.deltakerlisteId }
        )

        if (skalSlettes.isNotEmpty() || skalLeggesTil.isNotEmpty()) {
            logger.info("Ansatt $ansattId koordinator roller lagt til: ${skalLeggesTil.size}, deaktivert: ${skalSlettes.size}")
        }
    }

    fun oppdaterVeiledertilganger(ansattId: UUID, veileder: List<Veileder>) {
        val nye = veileder.map { VeilederDeltakerHolder(-1, it.deltakerId, it.type) }

        val gamle = veilederDeltakerRepository.getAktive(ansattId)
            .map { VeilederDeltakerHolder(it.id, it.deltakerId, it.veilederType) }

        val skalSlettes = gamle.filter { !nye.contains(it) }
        val skalLeggesTil = nye.filter { !gamle.contains(it) }

        veilederDeltakerRepository.deaktiver(skalSlettes.map { it.id })
        veilederDeltakerRepository.leggTil(
            ansattId,
            skalLeggesTil.map {
                VeilederDeltakerRepository.VeilederDeltakerInput(
                    deltakerId = it.deltakerId,
                    veilederType = it.veilederType
                )
            }
        )

        if (skalSlettes.isNotEmpty() || skalLeggesTil.isNotEmpty()) {
            logger.info("Ansatt $ansattId veileder roller lagt til: ${skalLeggesTil.size}, deaktivert: ${skalSlettes.size}")
        }
    }

    private data class KoordinatorDeltakerHolder(val id: Int, val deltakerlisteId: UUID) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as KoordinatorDeltakerHolder

            return deltakerlisteId == other.deltakerlisteId
        }

        override fun hashCode(): Int {
            return deltakerlisteId.hashCode()
        }
    }

    private data class VeilederDeltakerHolder(
        val id: Int,
        val deltakerId: UUID,
        val veilederType: VeilederType
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as VeilederDeltakerHolder

            if (deltakerId != other.deltakerId) return false
            return veilederType == other.veilederType
        }

        override fun hashCode(): Int {
            var result = deltakerId.hashCode()
            result = 31 * result + veilederType.hashCode()
            return result
        }
    }
}
