package no.nav.arrangor.ansatt

import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.utils.Issuer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@ProtectedWithClaims(issuer = Issuer.AZURE_AD)
@RequestMapping("/api/service/ansatt")
class AnsattControllerServiceUser(
	private val ansattService: AnsattService
) {
	@PostMapping
	fun getAnsatt(@RequestBody body: AnsattRequestBody): Ansatt {
		return ansattService.get(body.personident) ?: throw NoSuchElementException("Ansatt fantes ikke eller kunne ikke opprettes.")
	}

	@GetMapping("{id}")
	fun get(@PathVariable("id") id: UUID): Ansatt = ansattService.get(id)
		?: throw NoSuchElementException("Ansatt $id eksisterer ikke.")

	data class AnsattRequestBody(
		val personident: String
	)
}
