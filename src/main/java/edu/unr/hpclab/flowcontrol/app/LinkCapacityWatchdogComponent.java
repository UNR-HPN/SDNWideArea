/*
 * Copyright 2021-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.unr.hpclab.flowcontrol.app;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.PortStatistics;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import static org.onosproject.net.device.DeviceEvent.Type.PORT_STATS_UPDATED;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true, service = {LinkCapacityWatchdogComponent.class})
public class LinkCapacityWatchdogComponent {
    private final Services services = Services.getInstance();
    private final Logger log = LoggerFactory.getLogger(getClass());
    InternalDeviceListener internalDeviceListener = new InternalDeviceListener();

    @Activate
    protected void activate() {
        services.deviceService.addListener(internalDeviceListener);
        services.cfgService.registerProperties(getClass());
        log.info("Service Started");
    }

    @Deactivate
    protected void deactivate() {
        services.cfgService.unregisterProperties(getClass(), false);
        log.info("Stopped");
        services.deviceService.removeListener(internalDeviceListener);
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceEvent.Type type = event.type();
            DeviceId deviceId = event.subject().id();
            if (type == PORT_STATS_UPDATED) {
                List<PortStatistics> portStatisticsList = services.deviceService.getPortDeltaStatistics(deviceId);
                portStatisticsList.forEach(portStatistics -> {
                    long bytesReceived = Util.getBytesReceivingRate(portStatistics);
                    long bytesSent = Util.getBytesSendingRate(portStatistics);
                    if (bytesReceived > 0) {
                        Set<Link> ingressLinks = services.linkService.getIngressLinks(new ConnectPoint(deviceId, portStatistics.portNumber()));
                        LinksInformationDatabase.updateLinksLatestRate(ingressLinks, bytesReceived);
                    }
                    if (bytesSent > 0) {
                        Set<Link> egressLinks = services.linkService.getEgressLinks(new ConnectPoint(deviceId, portStatistics.portNumber()));
                        LinksInformationDatabase.updateLinksLatestRate(egressLinks, bytesSent);
                    }
                });
            }
        }
    }

}
