package no.nav.arrangor.ingest

import no.nav.arrangor.ansatt.repositories.AnsattRepository
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.defaultVirksomhet
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.Arrangor
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

            rolleRepository.leggTilRoller(
                arrangor.roller.map {
                    RolleRepository.RolleInput(
                        ansattId = ansatt.id,
                        organisasjonsnummer = organisasjonsnummer,
                        rolle = it
                    )
                }
            )
        }
    }
}
