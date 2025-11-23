package no.nav.arrangor.client.enhetsregister

import no.nav.common.token_client.client.MachineToMachineTokenClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class EnhetsregisterClientConfiguration(
	@Value($$"${amt-enhetsregister.url}") private val baseUrl: String,
	@Value($$"${amt-enhetsregister.scope}") private val scope: String,
) {
	@Bean
	fun enhetsregisterClient(machineToMachineTokenClient: MachineToMachineTokenClient, objectMapper: ObjectMapper) = EnhetsregisterClient(
		baseUrl = baseUrl,
		tokenProvider = { machineToMachineTokenClient.createMachineToMachineToken(scope) },
		objectMapper = objectMapper,
	)
}
