package no.nav.arrangor.configuration

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.common.job.JobRunner
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@EnableScheduling
@Configuration
class Jobs(
	private val leaderElection: LeaderElection,
	private val arrangorService: ArrangorService,
	private val ansattService: AnsattService
) {
	@Scheduled(cron = "@hourly")
	@SchedulerLock(name = "oppdater_arrangører", lockAtMostFor = "120m")
	fun updateArrangorer() {
		if (leaderElection.isLeader()) {
			JobRunner.run("Oppdater arrangører") { arrangorService.oppdaterArrangorer() }
		}
	}

	@Scheduled(cron = "@hourly")
	@SchedulerLock(name = "oppdater_ansatte", lockAtMostFor = "120m")
	fun updateAnsatte() {
		if (leaderElection.isLeader()) {
			JobRunner.run("Oppdater ansatte") { ansattService.oppdaterAnsatte() }
		}
	}
}
