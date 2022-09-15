package edu.unr.hpclab.flowcontrol.app;

import edu.unr.hpclab.flowcontrol.app.host_messages.HostMessagesHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, service = {AppMainComponent.class})
public class AppMainComponent {
    private static final Logger log = LoggerFactory.getLogger(AppMainComponent.class);

    @Activate
    protected void activate() {
        try {
            MainFlowsPacketProcessor.INSTANCE.activate();
            CurrentTrafficDataBase.INSTANCE.activate();
            LinkCapacityWatchdog.INSTANCE.activate();
            LinksInformationDatabase.INSTANCE.activate();
            HostMessagesHandler.INSTANCE.activate();
        } catch (Exception e) {
            log.error("", e);
        }
        log.info("Application Started");
    }

    @Deactivate
    protected void deactivate() {
        MainFlowsPacketProcessor.INSTANCE.deactivate();
        CurrentTrafficDataBase.INSTANCE.deactivate();
        LinkCapacityWatchdog.INSTANCE.deactivate();
        LinksInformationDatabase.INSTANCE.deactivate();
        HostMessagesHandler.INSTANCE.deactivate();
        Services.deactivate();
        log.info("Application Stopped");
    }
}
