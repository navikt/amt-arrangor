package no.nav.arrangor.arrangor

import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.utils.Issuer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
@RequestMapping("/api/arrangor")
class ArrangorController(
    private val arrangorService: ArrangorService
) {

    @GetMapping("{id}")
    fun get(@PathVariable("id") id: UUID): Arrangor = arrangorService.get(id)
        ?: throw NoSuchElementException("Arrangør med id $id eksisterer ikke")

    @GetMapping("/organisasjonsnummer/{orgNr}")
    fun getByOrgNr(@PathVariable("orgNr") orgNr: String): Arrangor? = arrangorService.get(orgNr)
}
