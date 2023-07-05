package no.nav.arrangor.arrangor

import no.nav.arrangor.MetricsService
import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.ingest.PublishService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ArrangorService(
	private val arrangorRepository: ArrangorRepository,
	private val enhetsregisterClient: EnhetsregisterClient,
	private val publishService: PublishService,
	private val metricsService: MetricsService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun get(id: UUID): Arrangor? = arrangorRepository.get(id)
		.let { it?.toDomain() }

	fun get(orgnumre: List<String>): List<Arrangor> = arrangorRepository.getArrangorerMedOrgnumre(orgnumre)
		.map { it.toDomain() }

	fun get(orgNr: String): Arrangor? =
		arrangorRepository.get(orgNr)?.toDomain()

	fun getOrCreate(orgNr: String): Arrangor =
		getOrCreateArrangor(orgNr).toDomain()

	fun getOrCreate(orgnumre: List<String>): List<Arrangor> {
		val lagredeArrangorer = arrangorRepository.getArrangorerMedOrgnumre(orgnumre)
			.map { it.toDomain() }
		val orgnummerSomMangler = orgnumre.filterNot { orgnummer -> lagredeArrangorer.any { it.organisasjonsnummer == orgnummer } }
		val nyeArrangorer = orgnummerSomMangler.map { insertArrangor(it).toDomain() }
		return lagredeArrangorer + nyeArrangorer
	}

	fun getArrangorMedOverordnetArrangor(orgNr: String): ArrangorMedOverordnetArrangor {
		val arrangor = getOrCreateArrangor(orgNr)
		val overordnetArrangor = arrangor.overordnetArrangorId?.let {
			get(it)
		}
		return ArrangorMedOverordnetArrangor(
			id = arrangor.id,
			navn = arrangor.navn,
			organisasjonsnummer = arrangor.organisasjonsnummer,
			overordnetArrangor = overordnetArrangor
		)
	}

	fun getArrangorMedOverordnetArrangor(arrangorId: UUID): ArrangorMedOverordnetArrangor? {
		val arrangor = get(arrangorId) ?: return null
		val overordnetArrangor = arrangor.overordnetArrangorId?.let {
			get(it)
		}
		return ArrangorMedOverordnetArrangor(
			id = arrangor.id,
			navn = arrangor.navn,
			organisasjonsnummer = arrangor.organisasjonsnummer,
			overordnetArrangor = overordnetArrangor
		)
	}

	fun getArrangorerMedOverordnetArrangorForArrangorIder(arrangorIder: List<UUID>): List<ArrangorMedOverordnetArrangor> {
		val arrangorer = arrangorRepository.getArrangorerMedIder(arrangorIder)
		val unikeOverordnedeArrangorIder = arrangorer.mapNotNull { it.overordnetArrangorId }.distinct()
		val overordnedeArrangorer = arrangorRepository.getArrangorerMedIder(unikeOverordnedeArrangorIder)

		return arrangorer.map { arrangorDbo ->
			ArrangorMedOverordnetArrangor(
				id = arrangorDbo.id,
				navn = arrangorDbo.navn,
				organisasjonsnummer = arrangorDbo.organisasjonsnummer,
				overordnetArrangor = arrangorDbo.overordnetArrangorId?.let { overordnetArrangorId ->
					val overordnetArrangor = overordnedeArrangorer.find { overordnetArrangorId == it.id }
					overordnetArrangor?.let {
						Arrangor(
							id = it.id,
							navn = it.navn,
							organisasjonsnummer = it.organisasjonsnummer,
							overordnetArrangorId = it.overordnetArrangorId
						)
					}
				}
			)
		}
	}

	private fun getOrCreateArrangor(orgNr: String): ArrangorRepository.ArrangorDbo {
		val arrangor = arrangorRepository.get(orgNr)

		if (arrangor != null) {
			return arrangor
		}
		logger.info("Arrangør for orgnummer $orgNr mangler, oppretter arrangør..")

		return insertArrangor(orgNr)
	}

	private fun insertArrangor(orgNr: String): ArrangorRepository.ArrangorDbo {
		val virksomhet = enhetsregisterClient.hentVirksomhet(orgNr).getOrDefault(getDefaultVirksomhet(orgNr))
		val overordnetArrangor = virksomhet.overordnetEnhetOrganisasjonsnummer?.let {
			arrangorRepository.insertOrUpdate(
				ArrangorRepository.ArrangorDbo(
					id = UUID.randomUUID(),
					navn = virksomhet.overordnetEnhetNavn
						?: throw IllegalStateException("Navn burde vært satt for $orgNr's overordnet enhet (${virksomhet.overordnetEnhetOrganisasjonsnummer}"),
					organisasjonsnummer = virksomhet.overordnetEnhetOrganisasjonsnummer,
					overordnetArrangorId = null
				)
			)
				.also { publishService.publishArrangor(it.toDomain()) }
				.also { metricsService.incEndredeArrangorer() }
		}
		return arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = UUID.randomUUID(),
				navn = virksomhet.navn,
				organisasjonsnummer = virksomhet.organisasjonsnummer,
				overordnetArrangorId = overordnetArrangor?.id
			)
		)
			.also { publishService.publishArrangor(it.toDomain()) }
			.also { metricsService.incEndredeArrangorer() }
	}

	private fun getDefaultVirksomhet(organisasjonsnummer: String): Virksomhet {
		return Virksomhet(
			organisasjonsnummer = organisasjonsnummer,
			navn = "Ukjent virksomhet",
			overordnetEnhetOrganisasjonsnummer = null,
			overordnetEnhetNavn = null
		)
	}
}
