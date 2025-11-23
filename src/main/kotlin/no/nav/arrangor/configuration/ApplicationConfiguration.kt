package no.nav.arrangor.configuration

import no.nav.common.rest.filter.LogRequestFilter
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.MachineToMachineTokenClient
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableJwtTokenValidation
class ApplicationConfiguration {
	@Bean
	fun machineToMachineTokenClient(
		@Value($$"${nais.env.azureAppClientId}") azureAdClientId: String,
		@Value($$"${nais.env.azureOpenIdConfigTokenEndpoint}") azureTokenEndpoint: String,
		@Value($$"${nais.env.azureAppJWK}") azureAdJWK: String,
	): MachineToMachineTokenClient = AzureAdTokenClientBuilder
		.builder()
		.withClientId(azureAdClientId)
		.withTokenEndpointUrl(azureTokenEndpoint)
		.withPrivateJwk(azureAdJWK)
		.buildMachineToMachineTokenClient()

	@Bean
	fun logFilterRegistrationBean(): FilterRegistrationBean<LogRequestFilter> {
		val registration = FilterRegistrationBean<LogRequestFilter>()
		registration.setFilter(LogRequestFilter("amt-arrangor", false))
		registration.order = 1
		registration.addUrlPatterns("/*")
		return registration
	}
}
