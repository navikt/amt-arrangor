package no.nav.arrangor.ansatt

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.utils.DataUpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AnsattRolleService(
    private val rolleRepository: RolleRepository,
    private val altinnClient: AltinnAclClient,
    private val metricsService: MetricsService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getRoller(ansattId: UUID): List<RolleRepository.RolleDbo> {
        return rolleRepository.getAktiveRoller(ansattId)
    }

    fun oppdaterRoller(ansattId: UUID, personident: String): DataUpdateWrapper<List<RolleRepository.RolleDbo>> {
        val gamleRoller = dboToOrgRolle(rolleRepository.getAktiveRoller(ansattId))
        val nyeRoller = altinnToOrgRolle(altinnClient.hentRoller(personident).getOrThrow())

        val rollerSomSkalSlettes = gamleRoller.filter { !nyeRoller.contains(it) }
        val rollerSomSkalLeggesTil = nyeRoller.filter { !gamleRoller.contains(it) }

        rolleRepository.deaktiverRoller(rollerSomSkalSlettes.map { it.id })
            .also { logFjernet(ansattId, rollerSomSkalSlettes) }

        rolleRepository.leggTilRoller(rollerSomSkalLeggesTil.map { it.toInput(ansattId) })
            .also { logLagtTil(ansattId, rollerSomSkalLeggesTil) }

        return DataUpdateWrapper(
            isUpdated = rollerSomSkalSlettes.isNotEmpty() || rollerSomSkalLeggesTil.isNotEmpty(),
            data = rolleRepository.getAktiveRoller(ansattId)
        )
            .also { if(it.isUpdated) metricsService.incEndretAnsattRolle(rollerSomSkalLeggesTil.size + rollerSomSkalSlettes.size) }
    }

    private fun logFjernet(ansattId: UUID, fjernet: List<OrgRolle>) = fjernet.forEach {
        logger.info("Ansatt med $ansattId mistet ${it.rolle} hos ${it.organisasjonsnummer}")
    }

    private fun logLagtTil(ansattId: UUID, lagtTil: List<OrgRolle>) = lagtTil.forEach {
        logger.info("Ansatt med $ansattId fikk ${it.rolle} hos ${it.organisasjonsnummer}")
    }

    private fun dboToOrgRolle(roller: List<RolleRepository.RolleDbo>): List<OrgRolle> = roller
        .map { OrgRolle(it.id, it.organisasjonsnummer, it.rolle) }

    private fun altinnToOrgRolle(roller: List<AltinnRolle>): List<OrgRolle> = roller
        .flatMap { entry -> entry.roller.map { entry.organisasjonsnummer to it } }
        .map { OrgRolle(-1, it.first, it.second) }

    private data class OrgRolle(
        val id: Int,
        val organisasjonsnummer: String,
        val rolle: AnsattRolle
    ) {
        fun toInput(ansattId: UUID): RolleRepository.RolleInput = RolleRepository.RolleInput(
            ansattId,
            organisasjonsnummer,
            rolle
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as OrgRolle

            if (organisasjonsnummer != other.organisasjonsnummer) return false
            return rolle == other.rolle
        }

        override fun hashCode(): Int {
            var result = organisasjonsnummer.hashCode()
            result = 31 * result + rolle.hashCode()
            return result
        }
    }
}
