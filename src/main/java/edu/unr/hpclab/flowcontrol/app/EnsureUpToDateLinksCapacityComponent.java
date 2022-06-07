package edu.unr.hpclab.flowcontrol.app;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Component(immediate = true,
        service = {EnsureUpToDateLinksCapacityComponent.class}
)
public class EnsureUpToDateLinksCapacityComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnsureUpToDateLinksCapacityComponent.class);
    private final Timer timer = new Timer();

    @Activate
    protected void activate() {
        timer.schedule(new InternalTimerTask(), TimeUnit.SECONDS.toMillis(Util.POLL_FREQ * 5L), TimeUnit.SECONDS.toMillis(Util.POLL_FREQ * 5L));
    }

    @Deactivate
    protected void deactivate() {
        timer.cancel();
    }


    static class InternalTimerTask extends TimerTask {
        @Override
        public void run() {
            LinksInformationDatabase.getLinkInformationMap().forEach((link, tcu) -> {
                if (Util.ageInSeconds(LinksInformationDatabase.getLatestLinkUpdate(link)) > Util.POLL_FREQ * 5L || Util.ageInSeconds(LinksInformationDatabase.getPenalizationTime(link)) >= Util.POLL_FREQ * 5L) {
//                    LOGGER.warn("Link {} capacity hasn't been updated since {} seconds", link, Util.POLL_FREQ * 5L);
                    LinksInformationDatabase.forgetBandwidth(link);
                }
            });
        }
    }
}
