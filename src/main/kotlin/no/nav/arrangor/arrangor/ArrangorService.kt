package no.nav.arrangor.arrangor

import no.nav.arrangor.arrangor.domain.Arrangor
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import org.springframework.stereotype.Service
import java.util.*

@Service
class ArrangorService(
    private val arrangorRepository: ArrangorRepository,
    private val enhetsregisterClient: EnhetsregisterClient
) {

    fun get(id: UUID): Arrangor = arrangorRepository.get(id)

    fun get(orgNr: String): Arrangor? = arrangorRepository.get(orgNr) ?: leggTilOppdaterArrangor(orgNr)

    fun oppdaterArrangor(arrangor: Arrangor): Arrangor = leggTilOppdaterArrangor(arrangor.organisasjonsnummer)
        ?: throw IllegalStateException("Arrangør med orgNr ${arrangor.organisasjonsnummer} burde eksistert")

    private fun leggTilOppdaterArrangor(orgNr: String): Arrangor? {
        val virksomhet = enhetsregisterClient.hentVirksomhet(orgNr).getOrNull() ?: return null

        val overordnetArrangor = virksomhet.overordnetEnhetOrganisasjonsnummer?.let {
            arrangorRepository.insertOrUpdateArrangor(
                ArrangorRepository.ArrangorInput(
                    navn = virksomhet.overordnetEnhetNavn
                        ?: throw IllegalStateException("Navn burde vært satt for $orgNr's overordnet enhet (${virksomhet.overordnetEnhetOrganisasjonsnummer}"),
                    organisasjonsnummer = virksomhet.overordnetEnhetOrganisasjonsnummer,
                    overordnetArrangorId = null
                )
            )
        }

        return arrangorRepository.insertOrUpdateArrangor(
            ArrangorRepository.ArrangorInput(
                navn = virksomhet.navn,
                organisasjonsnummer = virksomhet.organisasjonsnummer,
                overordnetArrangorId = overordnetArrangor?.id
            )
        )
    }
}
