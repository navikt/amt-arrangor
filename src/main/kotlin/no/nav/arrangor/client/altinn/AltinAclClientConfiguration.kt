package no.nav.arrangor.client.altinn

import no.nav.common.token_client.client.MachineToMachineTokenClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AltinAclClientConfiguration(
	@Value("\${amt-altinn.url}") private val baseUrl: String,
	@Value("\${amt-altinn.scope}") private val scope: String,
) {
	@Bean
	fun altinnClient(machineToMachineTokenClient: MachineToMachineTokenClient): AltinnAclClient = AltinnAclClient(
		baseUrl = baseUrl,
		tokenProvider = { machineToMachineTokenClient.createMachineToMachineToken(scope) },
	)
}
