package no.nav.arrangor.ingest

import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.defaultVirksomhet
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.dto.ArrangorDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IngestService(
    private val arrangorService: ArrangorService,
    private val arrangorRepository: ArrangorRepository,
    private val enhetsregisterClient: EnhetsregisterClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun handleArrangor(arrangor: ArrangorDto) {
        var overordnetArrangor: Arrangor? = null

        if (arrangor.overordnetArrangorId != null) {
            val hentet = enhetsregisterClient.hentVirksomhet(arrangor.organisasjonsnummer).getOrThrow()
            val overordnetVirksomhet =
                enhetsregisterClient.hentVirksomhet(hentet.overordnetEnhetOrganisasjonsnummer!!).getOrElse {
                    logger.warn("Virksomhet med organisasjonsnummer ${hentet.overordnetEnhetOrganisasjonsnummer} finnes ikke, legger inn default")
                    defaultVirksomhet()
                }
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorRepository.ArrangorDto(
                    id = arrangor.overordnetArrangorId,
                    navn = overordnetVirksomhet.navn,
                    organisasjonsnummer = overordnetVirksomhet.organisasjonsnummer,
                    overordnetArrangorId = null
                )
            )
            overordnetArrangor = arrangorService.get(arrangor.overordnetArrangorId)
        }

        val inserted = arrangorRepository.insertOrUpdateArrangor(
            ArrangorRepository.ArrangorDto(
                id = arrangor.id,
                navn = arrangor.navn,
                organisasjonsnummer = arrangor.organisasjonsnummer,
                overordnetArrangorId = overordnetArrangor?.id
            )
        )

        arrangorService.addDeltakerlister(inserted.id, arrangor.deltakerlister.toSet())
        logger.info("Håndterte arrangør med id ${arrangor.id}")
    }
}
