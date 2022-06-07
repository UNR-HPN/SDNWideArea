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
import edu.unr.hpclab.flowcontrol.app.congestionevent.LinkCongestionEventListener;
import edu.unr.hpclab.flowcontrol.app.congestionevent.LinkCongestionListenerService;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.net.Link;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
        service = {CongestionListenerComponent.class},
        property = {
                "appName=Some Default String Value",
        })
public class CongestionListenerComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final LinkCongestionEventListener congestionEventListener = new InternalLinkCongestionEventListener();
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;
    ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    LinkCongestionListenerService linkCongestionListenerService;
    private String appName;

    @Activate
    protected void activate() {
        linkCongestionListenerService.addListener(congestionEventListener);
        cfgService.registerProperties(getClass());
        log.info("Service Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        linkCongestionListenerService.removeListener(congestionEventListener);
        log.info("Stopped");
        singleThreadExecutor.shutdown();
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            appName = get(properties, "appName");
        }
        log.info("Reconfigured");
    }


    private class InternalLinkCongestionEventListener implements LinkCongestionEventListener {
        @Override
        public void event(LinkCongestionEvent event) {
            Link link = event.subject();
//            log.debug("Link {} is {}", link, event.type());
            singleThreadExecutor.submit(() -> CongestionHelper.solveCongestionFacade(link));
        }
    }
}
