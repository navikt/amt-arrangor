package no.nav.arrangor.ingest

import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.dto.ArrangorDto
import no.nav.arrangor.utils.JsonUtils
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PublishService(
    private val template: KafkaTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishArrangor(arrangor: Arrangor) =
        template.send(ARRANGOR_TOPIC, JsonUtils.toJson(arrangor.toDto()))
            .also { logger.info("Publiserte arrangør med id ${arrangor.id}") }

    private fun Arrangor.toDto(): ArrangorDto = ArrangorDto(
        id = id,
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
        overordnetArrangorId = overordnetArrangorId,
        deltakerlister = deltakerlister.toList()
    )

}
