package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.domain.AnsattRolle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AnsattRolleService(
    private val rolleRepository: RolleRepository,
    private val altinnClient: AltinnAclClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun oppdaterRoller(ansattId: UUID, personident: String): List<RolleRepository.RolleDbo> {
        val gamleRoller = dboToOrgRolle(rolleRepository.getAktiveRoller(ansattId))
        val nyeRoller = altinnToOrgRolle(altinnClient.hentRoller(personident).getOrThrow())

        val rollerSomSkalSlettes = gamleRoller.filter { !nyeRoller.contains(it) }
        val rollerSomSkalLeggesTil = nyeRoller.filter { !gamleRoller.contains(it) }

        rolleRepository.deaktiverRoller(rollerSomSkalSlettes.map { it.id })
            .also { if (rollerSomSkalSlettes.isNotEmpty()) logger.info("Deaktiverte ${rollerSomSkalSlettes.size} roller for ansatt med id $ansattId") }

        rolleRepository.leggTilRoller(rollerSomSkalLeggesTil.map { it.toInput(ansattId) })
            .also { if (rollerSomSkalLeggesTil.isNotEmpty()) logger.info("La til ${rollerSomSkalLeggesTil.size} roller for ansatt med id $ansattId") }

        return rolleRepository.getAktiveRoller(ansattId)
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
    }
}
