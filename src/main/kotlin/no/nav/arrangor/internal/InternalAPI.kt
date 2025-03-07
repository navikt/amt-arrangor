package no.nav.arrangor.internal

import jakarta.servlet.http.HttpServletRequest
import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.consumer.PublishService
import no.nav.common.job.JobRunner
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/internal")
class InternalAPI(
	private val ansattService: AnsattService,
	private val publishService: PublishService,
) {
	private val log = LoggerFactory.getLogger(InternalAPI::class.java)

	@Unprotected
	@GetMapping("/ansatte/republiser")
	fun republiserAnsatte(
		servlet: HttpServletRequest,
		@RequestParam(value = "startFromOffset", required = false) startFromOffset: Int?,
	) {
		if (isInternal(servlet)) {
			JobRunner.runAsync("republiser-ansatte") {
				republiserAlleAnsatte(startFromOffset ?: 0)
			}
		} else {
			throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
		}
	}

	private fun republiserAlleAnsatte(startOffset: Int = 0) {
		var offset = startOffset
		var ansatte: List<Ansatt>

		do {
			ansatte = ansattService.getAll(offset, 500)
			ansatte.forEach { publishService.publishAnsatt(it) }

			log.info("Republiserte ansatte fra offset $offset til ${offset + ansatte.size}")
			offset += ansatte.size
		} while (ansatte.isNotEmpty())
	}

	private fun isInternal(servlet: HttpServletRequest): Boolean = servlet.remoteAddr == "127.0.0.1"
}
