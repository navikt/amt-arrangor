package no.nav.arrangor.arrangor

import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.utils.Issuer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = Issuer.AZURE_AD)
@RequestMapping("/api/service/arrangor")
class ArrangorControllerServiceUser(
	private val arrangorService: ArrangorService
) {
	@GetMapping("/organisasjonsnummer/{orgnummer}")
	fun getArrangor(@PathVariable("orgnummer") orgnummer: String): ArrangorMedOverordnetArrangor {
		return arrangorService.getArrangorMedOverordnetArrangor(orgnummer)
	}
}
