package edu.unr.hpclab.flowcontrol.app.congestionevent;


import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.event.EventDeliveryService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true,
        service = {LinkCongestionListenerService.class}
)

public class LinkCongestionListenerManager extends AbstractListenerManager<LinkCongestionEvent, LinkCongestionEventListener> implements LinkCongestionListenerService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    protected ApplicationId appId;

    @Activate
    protected void activate() {
        eventDispatcher.addSink(LinkCongestionEvent.class, listenerRegistry);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        eventDispatcher.removeSink(LinkCongestionEvent.class);
        log.info("Stopped");
    }

    @Override
    public void post(LinkCongestionEvent event) {
        if (eventDispatcher == null) {
            eventDispatcher = DefaultServiceDirectory.getService(EventDeliveryService.class);
        }
        super.post(event);
    }
}