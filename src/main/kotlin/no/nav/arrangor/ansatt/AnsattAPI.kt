package no.nav.arrangor.ansatt

import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.VeilederType
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
@RequestMapping("/api/ansatt")
class AnsattAPI(
    private val ansattService: AnsattService,
) {
    @GetMapping
    fun getByPersonident(
        @AuthenticationPrincipal jwt: Jwt,
    ): Ansatt = hentPersonligIdentTilInnloggetBruker(jwt).let { personident ->
        ansattService.get(personident)
            ?: throw NoSuchElementException("Ansatt fantes ikke eller kunne ikke opprettes.")
    }

    @PostMapping("koordinator/{arrangorId}/{deltakerlisteId}")
    fun setKoordinatorForDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
        @PathVariable arrangorId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Ansatt = hentPersonligIdentTilInnloggetBruker(jwt).let { personident ->
        ansattService.setKoordinatorForDeltakerliste(
            personident = personident,
            deltakerlisteId = deltakerlisteId,
            arrangorId = arrangorId,
        )
    }

    @DeleteMapping("koordinator/{arrangorId}/{deltakerlisteId}")
    fun fjernKoordinatorForDeltakerliste(
        @PathVariable deltakerlisteId: UUID,
        @PathVariable arrangorId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Ansatt = hentPersonligIdentTilInnloggetBruker(jwt).let { personident ->
        ansattService.fjernKoordinatorForDeltakerliste(
            personident = personident,
            deltakerlisteId = deltakerlisteId,
            arrangorId = arrangorId,
        )
    }

    @PostMapping("veiledere/{deltakerId}")
    fun oppdaterVeiledereForDeltaker(
        @PathVariable deltakerId: UUID,
        @RequestBody request: OppdaterVeiledereForDeltakerRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        hentPersonligIdentTilInnloggetBruker(jwt).let { personident ->
            ansattService.oppdaterVeiledereForDeltaker(
                personident = personident,
                deltakerId = deltakerId,
                request = request,
            )
        }
    }

    private fun hentPersonligIdentTilInnloggetBruker(jwt: Jwt): String = jwt
        .getClaimAsString("pid")
        ?.also {
            ansattService
                .getAnsattIdForPersonident(it)
                ?.let { id -> MDC.put("ansatt-id", id.toString()) }
        }
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "PID is missing or is not a string")

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
