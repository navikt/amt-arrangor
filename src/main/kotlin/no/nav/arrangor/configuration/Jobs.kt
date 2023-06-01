package no.nav.arrangor.configuration

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.arrangor.ansatt.AnsattService
import no.nav.common.job.JobRunner
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@EnableScheduling
@Configuration
class Jobs(
	private val leaderElection: LeaderElection,
	private val ansattService: AnsattService
) {
	@Scheduled(cron = "@hourly")
	@SchedulerLock(name = "oppdater_roller", lockAtMostFor = "120m")
	fun updateRoller() {
		if (leaderElection.isLeader()) {
			JobRunner.run("Oppdater roller") { ansattService.oppdaterAnsattesRoller() }
		}
	}
}
