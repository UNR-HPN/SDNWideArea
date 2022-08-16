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

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.LinkDstCompleter;
import org.onosproject.cli.net.LinkSrcCompleter;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.net.link.LinkService;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;

import static edu.unr.hpclab.flowcontrol.app.Util.formatLink;

/**
 * Sample Apache Karaf CLI command.
 */
@Service
@Command(scope = "onos", name = "flow-control",
        description = "Sample Apache Karaf CLI command")
public class AppCommand extends AbstractShellCommand implements org.apache.karaf.shell.api.console.Completer {
    @Argument(name = "type", description = "type of stats to print",
            required = true)
    @Completion(AppCommand.class)
    String type = "";

    @Argument(index = 1, name = "one", description = "One link end-point as device/port",
            required = false, multiValued = false)
    @Completion(LinkSrcCompleter.class)
    String one = null;

    @Argument(index = 2, name = "two", description = "Another link end-point as device/port",
            required = false, multiValued = false)
    @Completion(LinkDstCompleter.class)
    String two = null;

    @Argument(index = 3, name = "capacity", description = "capacity",
            required = false, multiValued = false)
    String capacity = null;


    @Override
    protected void doExecute() {
        LinkService linkService = get(LinkService.class);
        Link link = one != null && two != null ? linkService.getLink(ConnectPoint.deviceConnectPoint(one), ConnectPoint.deviceConnectPoint(two)) : null;
        try {
            switch (type) {
                case "current-traffic":
                    CurrentTrafficDataBase.getCurrentTraffic().forEach((k, v) -> print("SrcDstPair %s, time %s", v, new Date(v.getTimeStarted())));
                    break;
                case "links-bandwidth":
                    if (link != null) {
//                    BottleneckDetector.testPathLatency(link);
                        print("Link %s has %s Bps", formatLink(link), LinksInformationDatabase.getLinkEstimatedBandwidth(link));
                    } else {
                        LinksInformationDatabase.getLinkInformationMap().values().forEach(v -> print("%s", v));
                    }
                    break;
                case "links-utilization":
                    if (link != null) {
                        double util = LinksInformationDatabase.getLinkUtilization(link) * 100;
                        print("Link %s with bandwidth %s is being used by %s%%", formatLink(link), LinksInformationDatabase.getLinkEstimatedBandwidth(link), String.format("%.2f", util));
                    } else {
                        LinksInformationDatabase.getLinkInformationMap().keySet().forEach(l -> {
                            print("Link %s with bandwidth %s is being used by %s%%", formatLink(l), LinksInformationDatabase.getLinkEstimatedBandwidth(l), String.format("%.2f", LinksInformationDatabase.getLinkUtilization(l)));
                        });
                    }
                    break;
                case "clear-links-capacity":
                    LinksInformationDatabase.deleteEntries();
                    break;
                case "test-latency":
                    DelayCalculatorSingleton.getInstance().testLinksLatency();
                    break;
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        // Delegate string completer
        StringsCompleter delegate = new StringsCompleter();
        SortedSet<String> strings = delegate.getStrings();
        strings.add("links-bandwidth");
        strings.add("links-utilization");
        strings.add("clear-links-capacity");
        strings.add("congested-links");
        strings.add("current-traffic");
        strings.add("update-link-bandwidth");
        strings.add("test-latency");

        // Now let the completer do the work for figuring out what to offer.
        return delegate.complete(session, commandLine, candidates);
    }

}
