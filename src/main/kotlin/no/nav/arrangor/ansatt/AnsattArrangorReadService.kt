package no.nav.arrangor.ansatt

import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AnsattArrangorReadService(
    private val ansattRepository: AnsattRepository,
    private val ansattArrangorRepository: AnsattArrangorRepository,
    private val featureToggle: AnsattArrangorFeatureToggle,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun ansattMedValgtArrangorkilde(ansatt: AnsattDbo): AnsattDbo = ansatteMedValgtArrangorkilde(listOf(ansatt)).single()

    fun ansatteMedValgtArrangorkilde(ansatte: List<AnsattDbo>): List<AnsattDbo> {
        if (ansatte.isEmpty() || !lesFraNormaliserteTabeller()) return ansatte

        return ansatteMedNormaliserteArrangorer(ansatte)
    }

    private fun ansatteMedNormaliserteArrangorer(ansatte: List<AnsattDbo>): List<AnsattDbo> {
        val arrangorerPerAnsatt = ansattArrangorRepository.getArrangorerForAnsatte(ansatte.map { it.id })
        return ansatte.map { ansatt ->
            ansatt.copy(arrangorer = arrangorerPerAnsatt[ansatt.id].orEmpty())
        }
    }

    fun getAnsatteHosArrangor(arrangorId: UUID): List<AnsattDbo> {
        if (!lesFraNormaliserteTabeller()) {
            return ansattRepository.getAnsatteHosArrangor(arrangorId)
        }

        val ansattIder = ansattArrangorRepository.getAnsattIderForArrangor(arrangorId)
        return ansatteMedNormaliserteArrangorer(ansattRepository.getAnsatte(ansattIder))
    }

    fun endredeAnsatteFraValgtKilde(endring: EndredeAnsatte): List<AnsattDbo> {
        if (!lesFraNormaliserteTabeller()) return endring.ansatteEndretIJsonb

        return ansatteMedNormaliserteArrangorer(
            ansattRepository.getAnsatte(endring.ansattIderEndretINormaliserteTabeller.distinct()),
        )
    }

    private fun lesFraNormaliserteTabeller(): Boolean {
        val lesFraNormaliserteTabeller = featureToggle.lesFraNormaliserteTabeller()

        logger.info(
            "Lesekilde for ansatt-arrangør er {}",
            if (lesFraNormaliserteTabeller) "normaliserte tabeller" else "JSONB",
        )

        return lesFraNormaliserteTabeller
    }
}
