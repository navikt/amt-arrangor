package no.nav.arrangor.client.person

import no.nav.common.token_client.client.MachineToMachineTokenClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class PersonClientConfiguration(
	@Value($$"${amt-person.url}") private val baseUrl: String,
	@Value($$"${amt-person.scope}") private val scope: String,
) {
	@Bean
	fun personClient(machineToMachineTokenClient: MachineToMachineTokenClient, objectMapper: ObjectMapper) = PersonClient(
		baseUrl = baseUrl,
		tokenProvider = { machineToMachineTokenClient.createMachineToMachineToken(scope) },
		objectMapper = objectMapper,
	)
}
