package no.nav.arrangor.configuration

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import no.nav.arrangor.ansatt.AnsattArrangorFeatureToggle
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
class UnleashConfiguration {
    @Bean(destroyMethod = "shutdown")
    @Profile("!local & !test")
    fun unleashClient(
        @Value($$"${app.unleash.url}") unleashUrl: String,
        @Value($$"${app.unleash.api-token}") unleashApiToken: String,
    ): Unleash = DefaultUnleash(
        UnleashConfig
            .builder()
            .appName(APP_NAME)
            .instanceId(APP_NAME)
            .unleashAPI(unleashUrl)
            .apiKey(unleashApiToken)
            .build(),
    )

    @Bean
    @Profile("!local & !test")
    fun ansattArrangorFeatureToggle(unleash: Unleash) = AnsattArrangorFeatureToggle {
        unleash.isEnabled(LES_NORMALISERTE_ANSATT_ARRANGORER)
    }

    @Bean
    @Profile("local", "test")
    fun lokalAnsattArrangorFeatureToggle(
        @Value($$"${app.feature-toggle.les-normaliserte-ansatt-arrangorer:false}") enabled: Boolean,
    ) = AnsattArrangorFeatureToggle { enabled }

    companion object {
        const val APP_NAME = "amt-arrangor"
        const val LES_NORMALISERTE_ANSATT_ARRANGORER = "amt.arrangor-les-normaliserte-tabeller"
    }
}
