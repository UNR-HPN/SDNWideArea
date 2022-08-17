package edu.unr.hpclab.flowcontrol.app;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.PortStatistics;

import java.util.List;
import java.util.Set;

import static org.onosproject.net.device.DeviceEvent.Type.PORT_STATS_UPDATED;

public class LinkCapacityWatchdog {
    public static final LinkCapacityWatchdog INSTANCE = new LinkCapacityWatchdog();
    private InternalDeviceListener internalDeviceListener;

    protected void activate() {
        internalDeviceListener = new InternalDeviceListener();
        Services.deviceService.addListener(internalDeviceListener);
    }

    protected void deactivate() {
        Services.deviceService.removeListener(internalDeviceListener);
    }

    private static class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceEvent.Type type = event.type();
            DeviceId deviceId = event.subject().id();
            if (type == PORT_STATS_UPDATED) {
                List<PortStatistics> portStatisticsList = Services.deviceService.getPortDeltaStatistics(deviceId);
                portStatisticsList.forEach(portStatistics -> {
                    long bytesReceived = Util.getBytesReceivingRate(portStatistics);
                    long bytesSent = Util.getBytesSendingRate(portStatistics);
                    if (bytesReceived > 0) {
                        Set<Link> ingressLinks = Services.linkService.getIngressLinks(new ConnectPoint(deviceId, portStatistics.portNumber()));
                        LinksInformationDatabase.updateLinksLatestRate(ingressLinks, bytesReceived);
                    }
                    if (bytesSent > 0) {
                        Set<Link> egressLinks = Services.linkService.getEgressLinks(new ConnectPoint(deviceId, portStatistics.portNumber()));
                        LinksInformationDatabase.updateLinksLatestRate(egressLinks, bytesSent);
                    }
                });
            }
        }
    }
}
