package no.nav.arrangor.testutils

import org.apache.kafka.clients.consumer.Consumer

fun <K, V> Consumer<K, V>.subscribeHvisIkkeSubscribed(vararg topics: String) {
	if (this.subscription().isEmpty()) {
		this.subscribe(listOf(*topics))
	}
}
