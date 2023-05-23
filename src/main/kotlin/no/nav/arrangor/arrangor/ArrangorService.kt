package no.nav.arrangor.arrangor

import no.nav.arrangor.MetricsService
import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.deltakerliste.DeltakerlisteRepository
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.ingest.PublishService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/*
    TODO: Mangler publisering om endringer
 */
@Service
class ArrangorService(
	private val arrangorRepository: ArrangorRepository,
	private val deltakerlisteRepository: DeltakerlisteRepository,
	private val enhetsregisterClient: EnhetsregisterClient,
	private val publishService: PublishService,
	private val metricsService: MetricsService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun get(id: UUID): Arrangor? = arrangorRepository.get(id)
		.let { it?.toDomain(deltakerlisteRepository.getDeltakerlisterForArrangor(it.id)) }

	fun get(orgNr: String): Arrangor = (
		arrangorRepository.get(orgNr)
			?: leggTilOppdaterArrangor(orgNr)
		)
		.let { it.toDomain(deltakerlisteRepository.getDeltakerlisterForArrangor(it.id)) }

	fun getArrangorMedOverordnetArrangor(orgNr: String): ArrangorMedOverordnetArrangor {
		val arrangor = arrangorRepository.get(orgNr)
			?: leggTilOppdaterArrangor(orgNr)
		val overordnetArrangor = arrangor.overordnetArrangorId?.let {
			get(it)
		}
		return ArrangorMedOverordnetArrangor(
			id = arrangor.id,
			navn = arrangor.navn,
			organisasjonsnummer = arrangor.organisasjonsnummer,
			overordnetArrangorId = arrangor.overordnetArrangorId,
			overordnetArrangorNavn = overordnetArrangor?.navn,
			overordnetArrangorOrgnummer = overordnetArrangor?.organisasjonsnummer,
			deltakerlister = deltakerlisteRepository.getDeltakerlisterForArrangor(arrangor.id)
		)
	}

	fun addDeltakerlister(arrangorId: UUID, deltakerlisteIds: Set<UUID>) =
		deltakerlisteRepository.addUpdateDeltakerlister(arrangorId, deltakerlisteIds)

	fun oppdaterArrangorer(limit: Int = 50, synchronizedBefore: LocalDateTime = LocalDateTime.now().minusDays(1)) =
		arrangorRepository.getToSynchronize(limit, synchronizedBefore)
			.map { oppdaterArrangor(it) }
			.also { logger.info("Oppdaterte ${it.size} arrangører") }

	fun oppdaterArrangor(arrangor: ArrangorRepository.ArrangorDbo): ArrangorRepository.ArrangorDbo =
		leggTilOppdaterArrangor(arrangor.organisasjonsnummer)

	private fun leggTilOppdaterArrangor(orgNr: String): ArrangorRepository.ArrangorDbo {
		val arrangor = arrangorRepository.get(orgNr)

		val oldVirksomhet = arrangor?.let { oar ->
			val overordnet = oar.overordnetArrangorId?.let { arrangorRepository.get(it) }
			Virksomhet(oar.organisasjonsnummer, oar.navn, overordnet?.organisasjonsnummer, overordnet?.navn)
		}

		val virksomhet = enhetsregisterClient.hentVirksomhet(orgNr).getOrThrow()

		if (oldVirksomhet != virksomhet) {
			val overordnetArrangor = virksomhet.overordnetEnhetOrganisasjonsnummer?.let {
				arrangorRepository.insertOrUpdate(
					ArrangorRepository.ArrangorDbo(
						navn = virksomhet.overordnetEnhetNavn
							?: throw IllegalStateException("Navn burde vært satt for $orgNr's overordnet enhet (${virksomhet.overordnetEnhetOrganisasjonsnummer}"),
						organisasjonsnummer = virksomhet.overordnetEnhetOrganisasjonsnummer,
						overordnetArrangorId = null
					)
				)
			}

			return arrangorRepository.insertOrUpdate(
				ArrangorRepository.ArrangorDbo(
					navn = virksomhet.navn,
					organisasjonsnummer = virksomhet.organisasjonsnummer,
					overordnetArrangorId = overordnetArrangor?.id
				)
					.also { publishService.publishArrangor(it.toDomain(deltakerlisteRepository.getDeltakerlisterForArrangor(it.id))) }
					.also { metricsService.incEndredeArrangorer() }
			)
		}

		return arrangor
	}
}
