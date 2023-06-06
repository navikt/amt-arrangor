package no.nav.arrangor.ansatt

import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.utils.Issuer
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@ProtectedWithClaims(issuer = Issuer.TOKEN_X)
@RequestMapping("/api/ansatt")
class AnsattController(
	private val ansattService: AnsattService,
	private val contextHolder: TokenValidationContextHolder
) {

	@GetMapping("{id}")
	fun get(@PathVariable("id") id: UUID): Ansatt = ansattService.get(id)
		?: throw NoSuchElementException("Ansatt $id eksisterer ikke.")

	@GetMapping
	fun getByPersonident(): Ansatt =
		hentPersonligIdentTilInnloggetBruker().let { personident ->
			ansattService.get(personident)
				?: throw NoSuchElementException("Ansatt fantes ikke eller kunne ikke opprettes.")
		}

	@PostMapping("koordinator/{arrangorId}/{deltakerlisteId}")
	fun setKoordinatorForDeltakerliste(
		@PathVariable("deltakerlisteId") deltakerlisteId: UUID,
		@PathVariable("arrangorId") arrangorId: UUID
	): Ansatt = hentPersonligIdentTilInnloggetBruker().let { personident ->
		ansattService.setKoordinatorForDeltakerliste(personident = personident, deltakerlisteId = deltakerlisteId, arrangorId = arrangorId)
	}

	@DeleteMapping("koordinator/{arrangorId}/{deltakerlisteId}")
	fun fjernKoordinatorForDeltakerliste(
		@PathVariable("deltakerlisteId") deltakerlisteId: UUID,
		@PathVariable("arrangorId") arrangorId: UUID
	): Ansatt = hentPersonligIdentTilInnloggetBruker().let { personident ->
		ansattService.fjernKoordinatorForDeltakerliste(personident = personident, deltakerlisteId = deltakerlisteId, arrangorId = arrangorId)
	}

	@PostMapping("veileder")
	fun setVeileder(
		@RequestBody body: SetVeilederForDeltakerRequestBody
	): Ansatt = hentPersonligIdentTilInnloggetBruker().let { personident ->
		ansattService.setVeileder(personident = personident, deltakerId = body.deltakerId, arrangorId = body.arrangorId, type = body.type)
	}

	@DeleteMapping("veileder/{arrangorId}/{deltakerId}/{type}")
	fun fjernVeilederForDeltaker(
		@PathVariable("deltakerId") deltakerId: UUID,
		@PathVariable("arrangorId") arrangorId: UUID,
		@PathVariable("type") type: VeilederType
	): Ansatt = hentPersonligIdentTilInnloggetBruker().let { personident ->
		ansattService.fjernVeileder(personident = personident, deltakerId = deltakerId, arrangorId = arrangorId, type = type)
	}

	private fun hentPersonligIdentTilInnloggetBruker(): String {
		val context = contextHolder.tokenValidationContext

		val token = context.firstValidToken.orElseThrow {
			throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authorized, valid token is missing")
		}

		return token.jwtTokenClaims["pid"]?.toString()
			?.also {
				ansattService.getAnsattIdForPersonident(it)
					?.let { id -> MDC.put("ansatt-id", id.toString()) }
			}
			?: throw ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"PID is missing or is not a string"
			)
	}

	data class SetVeilederForDeltakerRequestBody(
		val deltakerId: UUID,
		val arrangorId: UUID,
		val type: VeilederType
	)
}
