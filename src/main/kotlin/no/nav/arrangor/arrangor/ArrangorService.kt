package no.nav.arrangor.arrangor

import no.nav.arrangor.MetricsService
import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.consumer.PublishService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ArrangorService(
	private val arrangorRepository: ArrangorRepository,
	private val enhetsregisterClient: EnhetsregisterClient,
	private val publishService: PublishService,
	private val metricsService: MetricsService,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun getOrCreate(orgNr: String): Arrangor {
		val arrangor = arrangorRepository.get(orgNr)

		if (arrangor != null) {
			return arrangor.toDomain()
		}
		logger.info("Arrangør for orgnummer $orgNr mangler, oppretter arrangør..")

		return insertArrangor(orgNr).toDomain()
	}

	fun getOrCreate(orgnumre: List<String>): List<Arrangor> {
		val lagredeArrangorer =
			arrangorRepository
				.getArrangorerMedOrgnumre(orgnumre)
				.map { it.toDomain() }
		val orgnummerSomMangler =
			orgnumre.filterNot { orgnummer -> lagredeArrangorer.any { it.organisasjonsnummer == orgnummer } }
		val nyeArrangorer = orgnummerSomMangler.map { insertArrangor(it).toDomain() }
		return lagredeArrangorer + nyeArrangorer
	}

	fun getArrangorMedOverordnetArrangor(orgnr: String): ArrangorMedOverordnetArrangor {
		val arrangor = getOrCreate(orgnr)
		val overordnetArrangor = arrangor.overordnetArrangorId?.let { arrangorRepository.get(it)?.toDomain() }
		return ArrangorMedOverordnetArrangor(
			id = arrangor.id,
			navn = arrangor.navn,
			organisasjonsnummer = arrangor.organisasjonsnummer,
			overordnetArrangor = overordnetArrangor,
		)
	}

	fun getArrangorMedOverordnetArrangor(id: UUID): ArrangorMedOverordnetArrangor? {
		val arrangor = arrangorRepository.get(id).let { it?.toDomain() } ?: return null

		val overordnetArrangor = arrangor.overordnetArrangorId?.let { arrangorRepository.get(it)?.toDomain() }
		return ArrangorMedOverordnetArrangor(
			id = arrangor.id,
			navn = arrangor.navn,
			organisasjonsnummer = arrangor.organisasjonsnummer,
			overordnetArrangor = overordnetArrangor,
		)
	}

	fun getArrangorerMedOverordnetArrangor(ider: List<UUID>): List<ArrangorMedOverordnetArrangor> {
		val arrangorer = arrangorRepository.getArrangorerMedIder(ider)
		val unikeOverordnedeArrangorIder = arrangorer.mapNotNull { it.overordnetArrangorId }.distinct()
		val overordnedeArrangorer = arrangorRepository.getArrangorerMedIder(unikeOverordnedeArrangorIder)

		return arrangorer.map { arrangorDbo ->
			ArrangorMedOverordnetArrangor(
				id = arrangorDbo.id,
				navn = arrangorDbo.navn,
				organisasjonsnummer = arrangorDbo.organisasjonsnummer,
				overordnetArrangor =
					arrangorDbo.overordnetArrangorId?.let { overordnetArrangorId ->
						val overordnetArrangor = overordnedeArrangorer.find { overordnetArrangorId == it.id }
						overordnetArrangor?.let {
							Arrangor(
								id = it.id,
								navn = it.navn,
								organisasjonsnummer = it.organisasjonsnummer,
								overordnetArrangorId = it.overordnetArrangorId,
							)
						}
					},
			)
		}
	}

	private fun insertArrangor(orgNr: String): ArrangorRepository.ArrangorDbo {
		val virksomhet = enhetsregisterClient.hentVirksomhet(orgNr).getOrThrow()
		val overordnetArrangor = getOverordnetArrangor(virksomhet)
		val arrangor =
			arrangorRepository.insertOrUpdate(
				ArrangorRepository.ArrangorDbo(
					id = UUID.randomUUID(),
					navn = virksomhet.navn,
					organisasjonsnummer = virksomhet.organisasjonsnummer,
					overordnetArrangorId = overordnetArrangor?.id,
				),
			)
		publishService.publishArrangor(arrangor.toDomain())
		metricsService.incEndredeArrangorer()
		return arrangor
	}

	private fun getOverordnetArrangor(virksomhet: Virksomhet): ArrangorRepository.ArrangorDbo? {
		if (virksomhet.overordnetEnhetOrganisasjonsnummer == null || virksomhet.overordnetEnhetNavn == null) return null
		val overordnetArrangor =
			arrangorRepository.insertOrUpdate(
				ArrangorRepository.ArrangorDbo(
					id = UUID.randomUUID(),
					navn = virksomhet.overordnetEnhetNavn,
					organisasjonsnummer = virksomhet.overordnetEnhetOrganisasjonsnummer,
					overordnetArrangorId = null,
				),
			)
		publishService.publishArrangor(overordnetArrangor.toDomain())
		metricsService.incEndredeArrangorer()
		return overordnetArrangor
	}
}
