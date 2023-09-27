package no.nav.arrangor.ansatt

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.utils.DataUpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZonedDateTime
import java.util.UUID

@Service
class AnsattRolleService(
	private val altinnClient: AltinnAclClient,
	private val metricsService: MetricsService,
	private val arrangorService: ArrangorService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun getRollerFraAltinn(personIdent: String): List<AltinnRolle> {
		return altinnClient.hentRoller(personIdent).getOrThrow()
	}

	fun mapAltinnRollerTilArrangorListeForNyAnsatt(roller: List<AltinnRolle>): List<ArrangorDbo> {
		val unikeOrgnummer = roller.map { it.organisasjonsnummer }.distinct()
		val arrangorer = unikeOrgnummer.map { arrangorService.getOrCreate(it) }

		val arrangorListe = roller.mapNotNull { altinnRolle ->
			val arrangor = arrangorer.find { altinnRolle.organisasjonsnummer == it.organisasjonsnummer }
			if (arrangor != null) {
				ArrangorDbo(
					arrangorId = arrangor.id,
					roller = altinnRolle.roller.map { RolleDbo(it) },
					veileder = emptyList(),
					koordinator = emptyList()
				)
			} else {
				null
			}
		}
		return arrangorListe
	}

	fun getAnsattDboMedOppdaterteRoller(ansattDbo: AnsattDbo): DataUpdateWrapper<AnsattDbo> {
		val nyeRollerFraAltinn = altinnClient.hentRoller(ansattDbo.personident).getOrThrow()

		val unikeOrgnummerFraAltinn = nyeRollerFraAltinn.map { it.organisasjonsnummer }.distinct()
		val unikeArrangorerFraAltinnMedRolle = arrangorService.getOrCreate(unikeOrgnummerFraAltinn)

		val nyeRoller = altinnToRolleOgArrangor(nyeRollerFraAltinn, unikeArrangorerFraAltinnMedRolle)

		return getAnsattDboMedOppdaterteRoller(ansattDbo, nyeRoller)
	}

	private fun getAnsattDboMedOppdaterteRoller(
		ansattDbo: AnsattDbo,
		nyeRoller: List<RolleOgArrangor>
	): DataUpdateWrapper<AnsattDbo> {
		val gamleAktiveRoller = ansattDbo.arrangorer.flatMap { arrangor ->
			arrangor.roller.filter { it.erGyldig() }
				.map {
					RolleOgArrangor(
						arrangorId = arrangor.arrangorId,
						rolle = it.rolle,
						veileder = arrangor.veileder,
						koordinator = arrangor.koordinator
					)
				}
		}

		val rollerSomSkalDeaktiveres = gamleAktiveRoller
			.filter { nyeRoller.find { nyRolle -> nyRolle.rolle == it.rolle && nyRolle.arrangorId == it.arrangorId } == null }
		val rollerSomSkalLeggesTil = nyeRoller
			.filter { gamleAktiveRoller.find { gammelRolle -> gammelRolle.rolle == it.rolle && gammelRolle.arrangorId == it.arrangorId } == null }

		val oppdaterteArrangorerForAnsatt =
			getOppdaterteArrangorerForAnsatt(ansattDbo, rollerSomSkalDeaktiveres, rollerSomSkalLeggesTil)
		val oppdaterteArragorerMedDeltakerlisterOgVeiledere =
			leggTilOgFjernVeilederOgKoordinatorlister(oppdaterteArrangorerForAnsatt, nyeRoller)

		if (rollerSomSkalDeaktiveres.isNotEmpty()) {
			logFjernet(ansattDbo.id, rollerSomSkalDeaktiveres)
		}
		if (rollerSomSkalLeggesTil.isNotEmpty()) {
			logLagtTil(ansattDbo.id, rollerSomSkalLeggesTil)
		}

		val isUpdated =
			rollerSomSkalDeaktiveres.isNotEmpty() || rollerSomSkalLeggesTil.isNotEmpty() || oppdaterteArragorerMedDeltakerlisterOgVeiledere.isUpdated
		if (isUpdated) {
			metricsService.incEndretAnsattRolle(rollerSomSkalLeggesTil.size + rollerSomSkalDeaktiveres.size)
		}

		return DataUpdateWrapper(
			isUpdated = isUpdated,
			data = ansattDbo.copy(arrangorer = oppdaterteArragorerMedDeltakerlisterOgVeiledere.data)
		)
	}

	private fun getOppdaterteArrangorerForAnsatt(
		ansattDbo: AnsattDbo,
		rollerSomSkalDeaktiveres: List<RolleOgArrangor>,
		rollerSomSkalLeggesTil: List<RolleOgArrangor>
	): List<ArrangorDbo> {
		val oppdaterteArrangorer = mutableListOf<ArrangorDbo>()
		oppdaterteArrangorer.addAll(ansattDbo.arrangorer)

		rollerSomSkalDeaktiveres.forEach { rolleOgArrangor ->
			val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }
			if (arrangor != null) {
				if (rolleOgArrangor.rolle == AnsattRolle.KOORDINATOR) {
					arrangor.koordinator.forEach {
						if (it.erGyldig()) {
							it.gyldigTil = ZonedDateTime.now()
						}
					}
				}
				if (rolleOgArrangor.rolle == AnsattRolle.VEILEDER) {
					arrangor.veileder.forEach {
						if (it.erGyldig()) {
							it.gyldigTil = ZonedDateTime.now()
						}
					}
				}
				arrangor.roller.find { it.erGyldig() && it.rolle == rolleOgArrangor.rolle }
					?.let { it.gyldigTil = ZonedDateTime.now() }
			} else {
				logger.warn("Kan ikke deaktivere rolle hos arrangør som ikke er koblet til ansatt, arrangørid ${rolleOgArrangor.arrangorId}, ansattId ${ansattDbo.id}")
			}
		}

		rollerSomSkalLeggesTil.forEach { rolleOgArrangor ->
			val arrangor = oppdaterteArrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }
			if (arrangor != null) {
				val eksisterendeRolle = arrangor.roller.find { it.rolle == rolleOgArrangor.rolle }
				if (eksisterendeRolle != null && !eksisterendeRolle.erGyldig()) {
					eksisterendeRolle.gyldigTil = null
				} else {
					val oppdaterteRoller = mutableListOf<RolleDbo>()
					oppdaterteRoller.addAll(arrangor.roller)
					oppdaterteRoller.add(RolleDbo(rolleOgArrangor.rolle))
					val oppdatertArrangor = arrangor.copy(roller = oppdaterteRoller)
					oppdaterteArrangorer.removeIf { it.arrangorId == arrangor.arrangorId }
					oppdaterteArrangorer.add(oppdatertArrangor)
				}
			} else {
				oppdaterteArrangorer.add(
					ArrangorDbo(
						arrangorId = rolleOgArrangor.arrangorId,
						roller = listOf(RolleDbo(rolleOgArrangor.rolle)),
						koordinator = rolleOgArrangor.koordinator ?: emptyList(),
						veileder = rolleOgArrangor.veileder ?: emptyList()
					)
				)
			}
		}
		return oppdaterteArrangorer
	}

	private fun leggTilOgFjernVeilederOgKoordinatorlister(
		arrangorer: List<ArrangorDbo>,
		nyeRoller: List<RolleOgArrangor>
	): DataUpdateWrapper<List<ArrangorDbo>> {
		var isUpdated = false
		val nyeRollerUtenDuplikater = nyeRoller.distinctBy { it.arrangorId }

		val oppdaterteArrangorer = mutableListOf<ArrangorDbo>()

		nyeRollerUtenDuplikater.forEach { rolleOgArrangor ->
			val arrangor = arrangorer.find { it.arrangorId == rolleOgArrangor.arrangorId }
			if (arrangor != null && rolleOgArrangor.koordinator != null && rolleOgArrangor.veileder != null) {
				val deltakerlisterSomSkalSlettes = arrangor.koordinator.filter { deltakerliste ->
					deltakerliste.erGyldig() &&
						rolleOgArrangor.koordinator.find { it.deltakerlisteId == deltakerliste.deltakerlisteId } == null
				}
				val nyeDeltakerlister = rolleOgArrangor.koordinator.filter { deltakerliste ->
					arrangor.koordinator.find { it.erGyldig() && it.deltakerlisteId == deltakerliste.deltakerlisteId } == null
				}

				val deltakerlister = if (deltakerlisterSomSkalSlettes.isNotEmpty() || nyeDeltakerlister.isNotEmpty()) {
					val oppdatertDeltakerlisterForArrangor = mutableListOf<KoordinatorsDeltakerlisteDbo>()
					oppdatertDeltakerlisterForArrangor.addAll(arrangor.koordinator)
					deltakerlisterSomSkalSlettes.forEach { deltakerlisteDbo ->
						val koordinatorsDeltakerlisteDbo =
							oppdatertDeltakerlisterForArrangor.find { it.deltakerlisteId == deltakerlisteDbo.deltakerlisteId }
								?: throw IllegalArgumentException("Mangler deltakerliste som skal finnes")
						koordinatorsDeltakerlisteDbo.gyldigTil = ZonedDateTime.now()
					}
					nyeDeltakerlister.forEach { deltakerlisteDbo ->
						val koordinatorsDeltakerlisteDbo =
							oppdatertDeltakerlisterForArrangor.find { it.deltakerlisteId == deltakerlisteDbo.deltakerlisteId }
						if (koordinatorsDeltakerlisteDbo != null) {
							koordinatorsDeltakerlisteDbo.gyldigTil = null
						} else {
							oppdatertDeltakerlisterForArrangor.add(KoordinatorsDeltakerlisteDbo(deltakerlisteDbo.deltakerlisteId))
						}
					}
					isUpdated = true
					oppdatertDeltakerlisterForArrangor
				} else {
					arrangor.koordinator
				}

				val veiledereSomSkalSlettes = arrangor.veileder.filter { veileder ->
					veileder.erGyldig() &&
						rolleOgArrangor.veileder.find { it.deltakerId == veileder.deltakerId && it.veilederType == veileder.veilederType } == null
				}
				val nyeVeiledere = rolleOgArrangor.veileder.filter { veileder ->
					arrangor.veileder.find { it.erGyldig() && it.deltakerId == veileder.deltakerId && it.veilederType == veileder.veilederType } == null
				}

				val veiledere = if (veiledereSomSkalSlettes.isNotEmpty() || nyeVeiledere.isNotEmpty()) {
					val oppdaterteVeiledereForArrangor = mutableListOf<VeilederDeltakerDbo>()
					oppdaterteVeiledereForArrangor.addAll(arrangor.veileder)
					veiledereSomSkalSlettes.forEach { veilederDeltakerDbo ->
						val veiledereDbo =
							oppdaterteVeiledereForArrangor.find { it.deltakerId == veilederDeltakerDbo.deltakerId && it.veilederType == veilederDeltakerDbo.veilederType }
								?: throw IllegalArgumentException("Mangler deltaker som skal finnes")
						veiledereDbo.gyldigTil = ZonedDateTime.now()
					}
					nyeVeiledere.forEach { veilederDeltakerDbo ->
						val veiledereDbo =
							oppdaterteVeiledereForArrangor.find { it.deltakerId == veilederDeltakerDbo.deltakerId && it.veilederType == veilederDeltakerDbo.veilederType }
						if (veiledereDbo != null) {
							veiledereDbo.gyldigTil = null
						} else {
							oppdaterteVeiledereForArrangor.add(
								VeilederDeltakerDbo(
									veilederDeltakerDbo.deltakerId,
									veilederDeltakerDbo.veilederType
								)
							)
						}
					}
					isUpdated = true
					oppdaterteVeiledereForArrangor
				} else {
					arrangor.veileder
				}

				oppdaterteArrangorer.add(
					arrangor.copy(
						koordinator = deltakerlister,
						veileder = veiledere
					)
				)
			}
		}
		arrangorer.forEach { arrangor ->
			if (oppdaterteArrangorer.find { it.arrangorId == arrangor.arrangorId } == null) {
				oppdaterteArrangorer.add(arrangor)
			}
		}

		return DataUpdateWrapper(isUpdated, oppdaterteArrangorer)
	}

	private fun logFjernet(ansattId: UUID, fjernet: List<RolleOgArrangor>) = fjernet.forEach {
		logger.info("Ansatt med $ansattId mistet ${it.rolle} hos ${it.arrangorId}")
	}

	private fun logLagtTil(ansattId: UUID, lagtTil: List<RolleOgArrangor>) = lagtTil.forEach {
		logger.info("Ansatt med $ansattId fikk ${it.rolle} hos ${it.arrangorId}")
	}

	private fun altinnToRolleOgArrangor(roller: List<AltinnRolle>, arrangorer: List<Arrangor>): List<RolleOgArrangor> {
		return roller.flatMap { altinnRolle ->
			kombinerRollerOgArrangor(altinnRolle, arrangorer)
		}
	}

	private fun kombinerRollerOgArrangor(
		altinnRolle: AltinnRolle,
		arrangorer: List<Arrangor>
	) = altinnRolle.roller.mapNotNull { ansattRolle ->
		val arrangor = arrangorer.find { arrangor -> arrangor.organisasjonsnummer == altinnRolle.organisasjonsnummer }
			?: return@mapNotNull null
		RolleOgArrangor(
			arrangorId = arrangor.id,
			rolle = ansattRolle,
			veileder = null,
			koordinator = null
		)
	}

	private data class RolleOgArrangor(
		val arrangorId: UUID,
		val rolle: AnsattRolle,
		val veileder: List<VeilederDeltakerDbo>?,
		val koordinator: List<KoordinatorsDeltakerlisteDbo>?
	)
}
