package no.nav.arrangor.arrangor

import no.nav.arrangor.arrangor.domain.Arrangor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/arrangor")
class ArrangorController(
    private val arrangorService: ArrangorService
) {

    @GetMapping("/organisasjonsnummer/{orgNr}")
    fun get(@PathVariable("orgNr") orgNr: String): Arrangor? = arrangorService.get(orgNr)
}
