package no.nav.arrangor.arrangor

import no.nav.arrangor.domain.Arrangor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/arrangor")
class ArrangorController(
    private val arrangorService: ArrangorService
) {

    @GetMapping("{id}")
    fun get(@PathVariable("id") id: UUID): Arrangor = arrangorService.get(id) ?: throw IllegalStateException("Expected arrangor with id $id to exist")

    @GetMapping("/organisasjonsnummer/{orgNr}")
    fun getByOrgNr(@PathVariable("orgNr") orgNr: String): Arrangor? = arrangorService.get(orgNr)
}
