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


import edu.unr.hpclab.flowcontrol.app.congestionevent.LinkCongestionEvent;
import edu.unr.hpclab.flowcontrol.app.congestionevent.LinkCongestionListenerManager;
import org.onosproject.net.DefaultEdgeLink;
import org.onosproject.net.Link;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Skeletal ONOS application component.
 */

public class CongestionReporterComponent {
    static LinkCongestionListenerManager linkCongestionListenerManager = new LinkCongestionListenerManager();

    public static void checkCongestion() {
        CurrentTrafficDataBase.getCurrentTraffic().values().stream()
                .filter(ct -> Util.ageInSeconds(ct.getTimeStarted()) > Util.POLL_FREQ * 2)
                .map(srcDstTrafficInfo -> srcDstTrafficInfo.getCurrentPath().links())
                .map(links -> links.stream().filter(l -> !(l instanceof DefaultEdgeLink)).max(Comparator.comparing(LinksInformationDatabase::getLinkUtilization)))
                .distinct().collect(Collectors.toList())
                .forEach(l ->
                         {
                             if (l.isPresent() && LinksInformationDatabase.getLinkUtilization(l.get()) > 0.5) {
                                 reportCongested(LinkCongestionEvent.Type.HEAVILY_CONGESTED, l.get());
                             }
                         }
                );
    }

    static void reportCongested(LinkCongestionEvent.Type type, Link link) {
        linkCongestionListenerManager.post(new LinkCongestionEvent(type, link));
    }
}
