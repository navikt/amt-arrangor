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
class AnsattAPI(
	private val ansattService: AnsattService,
	private val contextHolder: TokenValidationContextHolder,
) {
	@GetMapping
	fun getByPersonident(): Ansatt = hentPersonligIdentTilInnloggetBruker().let { personident ->
		ansattService.get(personident)
			?: throw NoSuchElementException("Ansatt fantes ikke eller kunne ikke opprettes.")
	}

	@PostMapping("koordinator/{arrangorId}/{deltakerlisteId}")
	fun setKoordinatorForDeltakerliste(
		@PathVariable("deltakerlisteId") deltakerlisteId: UUID,
		@PathVariable("arrangorId") arrangorId: UUID,
	): Ansatt = hentPersonligIdentTilInnloggetBruker().let { personident ->
		ansattService.setKoordinatorForDeltakerliste(personident = personident, deltakerlisteId = deltakerlisteId, arrangorId = arrangorId)
	}

	@DeleteMapping("koordinator/{arrangorId}/{deltakerlisteId}")
	fun fjernKoordinatorForDeltakerliste(
		@PathVariable("deltakerlisteId") deltakerlisteId: UUID,
		@PathVariable("arrangorId") arrangorId: UUID,
	): Ansatt = hentPersonligIdentTilInnloggetBruker().let { personident ->
		ansattService.fjernKoordinatorForDeltakerliste(personident = personident, deltakerlisteId = deltakerlisteId, arrangorId = arrangorId)
	}

	@PostMapping("veiledere/{deltakerId}")
	fun oppdaterVeiledereForDeltaker(
		@PathVariable("deltakerId") deltakerId: UUID,
		@RequestBody request: OppdaterVeiledereForDeltakerRequest,
	) {
		hentPersonligIdentTilInnloggetBruker().let { personident ->
			ansattService.oppdaterVeiledereForDeltaker(
				personident = personident,
				deltakerId = deltakerId,
				request = request,
			)
		}
	}

	private fun hentPersonligIdentTilInnloggetBruker(): String {
		val context = contextHolder.getTokenValidationContext()

		val token =
			context.firstValidToken
				?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authorized, valid token is missing")

		return token.jwtTokenClaims
			.getStringClaim("pid")
			?.also {
				ansattService
					.getAnsattIdForPersonident(it)
					?.let { id -> MDC.put("ansatt-id", id.toString()) }
			}
			?: throw ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"PID is missing or is not a string",
			)
	}

	data class OppdaterVeiledereForDeltakerRequest(
		val arrangorId: UUID,
		val veilederSomLeggesTil: List<VeilederAnsatt>,
		val veilederSomFjernes: List<VeilederAnsatt>,
	)

	data class VeilederAnsatt(
		val ansattId: UUID,
		val type: VeilederType,
	)
}
