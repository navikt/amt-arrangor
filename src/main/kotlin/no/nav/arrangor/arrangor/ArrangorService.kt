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

/*
    TODO: Mangler publisering om endringer
 */
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

	fun getOrUpsert(orgNr: String): Arrangor = (
		arrangorRepository.get(orgNr)
			?: upsertArrangor(orgNr)
		).toDomain()

	fun getArrangorMedOverordnetArrangor(orgNr: String): ArrangorMedOverordnetArrangor {
		val arrangor = arrangorRepository.get(orgNr)
			?: upsertArrangor(orgNr)
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

	private fun upsertArrangor(orgNr: String): ArrangorRepository.ArrangorDbo {
		val arrangor = arrangorRepository.get(orgNr)

		val oldVirksomhet = arrangor?.let { oar ->
			val overordnet = oar.overordnetArrangorId?.let { arrangorRepository.get(it) }
			Virksomhet(oar.organisasjonsnummer, oar.navn, overordnet?.organisasjonsnummer, overordnet?.navn)
		}

		val virksomhet = enhetsregisterClient.hentVirksomhet(orgNr).getOrDefault(getDefaultVirksomhet(orgNr))

		if (oldVirksomhet != virksomhet) {
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
			}

			return arrangorRepository.insertOrUpdate(
				ArrangorRepository.ArrangorDbo(
					id = arrangor?.id ?: UUID.randomUUID(),
					navn = virksomhet.navn,
					organisasjonsnummer = virksomhet.organisasjonsnummer,
					overordnetArrangorId = overordnetArrangor?.id
				)
					.also { publishService.publishArrangor(it.toDomain()) }
					.also { metricsService.incEndredeArrangorer() }
			)
		}
		return arrangor
	}

	private fun getDefaultVirksomhet(organisasjonsnummer: String): Virksomhet {
		logger.warn("Kunne ikke hente virksomhet for orgnummer $organisasjonsnummer, bruker defaultverdier")
		return Virksomhet(
			organisasjonsnummer = organisasjonsnummer,
			navn = "Ukjent virksomhet",
			overordnetEnhetOrganisasjonsnummer = null,
			overordnetEnhetNavn = null
		)
	}
}
