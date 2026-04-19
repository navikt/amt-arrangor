package no.nav.arrangor.arrangor

import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.utils.Issuer
import no.nav.arrangor.utils.Orgnummer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@ProtectedWithClaims(issuer = Issuer.AZURE_AD)
@RequestMapping("/api/service/arrangor")
class ArrangorServiceUserAPI(
    private val arrangorService: ArrangorService,
) {
    @GetMapping("/organisasjonsnummer/{orgnummer}")
    fun getArrangor(
        @PathVariable orgnummer: String,
    ): ArrangorMedOverordnetArrangor {
        validerOrganisasjonsnummer(orgnummer)
        return arrangorService.getArrangorMedOverordnetArrangor(orgnummer)
    }

    @GetMapping("{id}")
    fun get(
        @PathVariable id: UUID,
    ): ArrangorMedOverordnetArrangor = arrangorService.getArrangorMedOverordnetArrangor(id)
        ?: throw NoSuchElementException("Arrangør med id $id eksisterer ikke")

    private fun validerOrganisasjonsnummer(organisasjonsnummer: String) {
        if (!Orgnummer.erGyldig(organisasjonsnummer.trim())) {
            throw IllegalArgumentException("Ugyldig organisasjonsnummer $organisasjonsnummer")
        }
    }
}
