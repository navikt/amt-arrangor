package no.nav.arrangor.consumer

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.consumer.model.AVSLUTTENDE_STATUSER
import no.nav.arrangor.consumer.model.AnsattPersonaliaDto
import no.nav.arrangor.consumer.model.Deltaker
import no.nav.arrangor.consumer.model.SKJULES_ALLTID_STATUSER
import no.nav.arrangor.consumer.model.VirksomhetDto
import no.nav.arrangor.deltaker.DeltakerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class ConsumerService(
	private val ansattRepository: AnsattRepository,
	private val ansattService: AnsattService,
	private val arrangorRepository: ArrangorRepository,
	private val enhetsregisterClient: EnhetsregisterClient,
	private val metricsService: MetricsService,
	private val publishService: PublishService,
	private val deltakerRepository: DeltakerRepository,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun handleVirksomhetEndring(virksomhetDto: VirksomhetDto?) {
		if (virksomhetDto == null) return

		val arrangor = arrangorRepository.get(virksomhetDto.organisasjonsnummer)

		if (arrangor != null) {
			val overordnetArrangorId =
				virksomhetDto.overordnetEnhetOrganisasjonsnummer?.let {
					getOverordnetArrangorId(overordnetEnhetOrganisasjonsnummer = it, arrangor = arrangor)
				}
			arrangorRepository
				.insertOrUpdate(
					arrangor.copy(
						navn = virksomhetDto.navn,
						organisasjonsnummer = virksomhetDto.organisasjonsnummer,
						overordnetArrangorId = overordnetArrangorId,
					),
				).also { publishService.publishArrangor(it.toDomain()) }
				.also { metricsService.incEndredeArrangorer() }
			logger.info("Oppdatert arrangør med id ${arrangor.id}")
			metricsService.incConsumedVirksomhetEndring()
		}
	}

	fun handleAnsattPersonalia(ansattPersonalia: AnsattPersonaliaDto) {
		val ansatt =
			ansattRepository.getByPersonId(ansattPersonalia.id)
				?: return logger.warn("Mottok personalia men fant ikke ansatt med personId ${ansattPersonalia.id}")

		if (harPersonaliaEndringer(ansatt, ansattPersonalia)) {
			ansattRepository.insertOrUpdate(
				ansatt.copy(
					fornavn = ansattPersonalia.fornavn,
					mellomnavn = ansattPersonalia.mellomnavn,
					etternavn = ansattPersonalia.etternavn,
					personident = ansattPersonalia.personident,
				),
			)
			logger.info("Oppdaterte personalia for ansatt ${ansatt.id}")
		}
	}

	private fun harPersonaliaEndringer(ansatt: AnsattDbo, ansattPersonalia: AnsattPersonaliaDto): Boolean =
		ansatt.personident != ansattPersonalia.personident ||
			ansatt.fornavn != ansattPersonalia.fornavn ||
			ansatt.mellomnavn != ansattPersonalia.mellomnavn ||
			ansatt.etternavn != ansattPersonalia.etternavn

	private fun getOverordnetArrangorId(overordnetEnhetOrganisasjonsnummer: String, arrangor: ArrangorRepository.ArrangorDbo): UUID? {
		val overordnetArrangorFraDb = arrangorRepository.get(overordnetEnhetOrganisasjonsnummer)
		if (overordnetArrangorFraDb?.id == arrangor.overordnetArrangorId) {
			return arrangor.overordnetArrangorId
		}

		return if (overordnetArrangorFraDb != null) {
			logger.info("Arrangør ${arrangor.id} har fått ny overordnet arrangør med id ${overordnetArrangorFraDb.id}")
			overordnetArrangorFraDb.id
		} else {
			logger.warn(
				"Fant ikke overordnet arrangør for orgnummer $overordnetEnhetOrganisasjonsnummer, oppretter overordnet " +
					"arrangør for arrangør ${arrangor.id}",
			)
			val nyOverordnetArrangor =
				enhetsregisterClient.hentVirksomhet(overordnetEnhetOrganisasjonsnummer).let { result ->
					result.getOrNull()?.let {
						arrangorRepository.insertOrUpdate(
							ArrangorRepository.ArrangorDbo(
								id = UUID.randomUUID(),
								navn = it.navn,
								organisasjonsnummer = it.organisasjonsnummer,
								overordnetArrangorId = null,
							),
						)
					}
				}
			if (nyOverordnetArrangor != null) {
				logger.info("Opprettet ny overordnet arrangør med id ${nyOverordnetArrangor.id}")
				publishService.publishArrangor(nyOverordnetArrangor.toDomain())
				metricsService.incEndredeArrangorer()
			} else {
				logger.warn("Kunne ikke opprette ovrordnet arrangør for orgnummer $overordnetEnhetOrganisasjonsnummer")
			}
			nyOverordnetArrangor?.id
		}
	}

	fun handleDeltakerEndring(id: UUID, deltaker: Deltaker?) {
		if (skalOppdatereVeiledereForDeltaker(id, deltaker)) {
			if (deltaker == null || deltaker.status.type in SKJULES_ALLTID_STATUSER || deltaker.status.type in AVSLUTTENDE_STATUSER) {
				val deaktiveringsdato = LocalDateTime.now().plusDays(50).atZone(ZoneId.systemDefault())
				// Deltakere fjernes fra deltakeroversikten 40 dager etter avsluttende status er satt,
				// så veiledere må ikke deaktiveres før den datoen er passert. For statuser som skjules umiddelbart deaktiverer vi
				// om 50 dager for litt sikkerhetsmargin i tilfelle deltaker blir aktiv igjen.
				ansattService.deaktiverVeiledereForDeltaker(
					deltakerId = id,
					deaktiveringsdato = deaktiveringsdato,
					status = deltaker?.status?.type,
				)
			} else {
				ansattService.maybeReaktiverVeiledereForDeltaker(id, deltaker.status.type)
			}
			deltaker?.let { deltakerRepository.insertOrUpdate(it) }
		}
	}

	private fun skalOppdatereVeiledereForDeltaker(id: UUID, deltaker: Deltaker?): Boolean {
		if (deltaker == null) {
			logger.info("Oppdaterer veilederkoblinger for tombstonet deltaker $id")
			return true
		}
		val lagretDeltaker = deltakerRepository.get(id)
		if (lagretDeltaker == null) {
			logger.info("Oppdaterer veilederkoblinger for deltaker som ikke er lagret $id")
			return true
		} else if (deltaker.status.type != lagretDeltaker.status.type) {
			logger.info("Oppdaterer veilederkoblinger for deltaker som har endret status $id")
			return true
		} else {
			logger.info("Deltaker $id har ikke endret status, ignorerer")
			return false
		}
	}
}
