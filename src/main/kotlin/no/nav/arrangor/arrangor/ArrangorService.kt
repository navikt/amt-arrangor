package no.nav.arrangor.arrangor

import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.deltakerliste.DeltakerlisteRepository
import no.nav.arrangor.domain.Arrangor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class ArrangorService(
    private val arrangorRepository: ArrangorRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val enhetsregisterClient: EnhetsregisterClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID): Arrangor? = arrangorRepository.get(id)
        .let { it?.toDomain(deltakerlisteRepository.getDeltakerlisterForArrangor(it.id)) }

    fun get(orgNr: String): Arrangor = (
        arrangorRepository.get(orgNr)
            ?: leggTilOppdaterArrangor(orgNr)
        )
        .let { it.toDomain(deltakerlisteRepository.getDeltakerlisterForArrangor(it.id)) }

    fun addDeltakerlister(arrangorId: UUID, deltakerlisteIds: Set<UUID>) =
        deltakerlisteRepository.addUpdateDeltakerlister(arrangorId, deltakerlisteIds)

    fun oppdaterArrangorer(limit: Int = 50, synchronizedBefore: LocalDateTime = LocalDateTime.now().minusDays(1)) =
        arrangorRepository.getToSynchronize(limit, synchronizedBefore)
            .map { oppdaterArrangor(it) }
            .also { logger.info("Oppdaterte ${it.size} arrangører") }

    fun oppdaterArrangor(arrangor: ArrangorRepository.ArrangorDto): ArrangorRepository.ArrangorDto =
        leggTilOppdaterArrangor(arrangor.organisasjonsnummer)

    private fun leggTilOppdaterArrangor(orgNr: String): ArrangorRepository.ArrangorDto {
        val virksomhet = enhetsregisterClient.hentVirksomhet(orgNr).getOrThrow()

        val overordnetArrangor = virksomhet.overordnetEnhetOrganisasjonsnummer?.let {
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorRepository.ArrangorDto(
                    navn = virksomhet.overordnetEnhetNavn
                        ?: throw IllegalStateException("Navn burde vært satt for $orgNr's overordnet enhet (${virksomhet.overordnetEnhetOrganisasjonsnummer}"),
                    organisasjonsnummer = virksomhet.overordnetEnhetOrganisasjonsnummer,
                    overordnetArrangorId = null
                )
            )
        }

        return arrangorRepository.insertOrUpdateArrangor(
            ArrangorRepository.ArrangorDto(
                navn = virksomhet.navn,
                organisasjonsnummer = virksomhet.organisasjonsnummer,
                overordnetArrangorId = overordnetArrangor?.id
            )
        )
    }
}
