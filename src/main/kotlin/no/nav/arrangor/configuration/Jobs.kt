package no.nav.arrangor.configuration

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.common.job.JobRunner
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@EnableScheduling
@Configuration
class Jobs(
    private val leaderElection: LeaderElection,
    private val arrangorService: ArrangorService
) {
    @Scheduled(cron = "@hourly")
    @SchedulerLock(name = "oppdater_arrangører", lockAtMostFor = "120m")
    fun updateArrangorer() {
        if (leaderElection.isLeader()) {
            JobRunner.run("Oppdater arrangører") { arrangorService.oppdaterArrangorer() }
        }
    }
}
