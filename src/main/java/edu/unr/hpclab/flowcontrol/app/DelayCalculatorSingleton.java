package edu.unr.hpclab.flowcontrol.app;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.onlab.graph.ScalarWeight;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ElementId;
import org.onosproject.net.Host;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.provider.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.onlab.packet.Ethernet.TYPE_IPV4;


public class DelayCalculatorSingleton {

    public static final IpAddress CONTROLLER_HOST_IP = IpAddress.valueOf(Optional.ofNullable(System.getenv("CONTROLLER_IP")).orElse("10.0.1.10"));
    //    public static final Host CONTROLLER_HOST = Services.hostService.getHostsByIp(CONTROLLER_HOST_IP).stream().findFirst().orElseThrow();
    public static final ApplicationId DELAY_CALCULATOR_APP_ID = Services.coreService.registerApplication("edu.unr.hpclab.flowcontrol.delaycalculator");
    private static final Logger log = LoggerFactory.getLogger(DelayCalculatorSingleton.class);
    private static final Map<DeviceId, Double> controlLinkLatencies = new ConcurrentHashMap<>();
    private static DelayCalculatorSingleton instance = null;

    private DelayCalculatorSingleton() {
    }

    public static DelayCalculatorSingleton getInstance() {   //Singleton
        if (instance == null) {
            try {
                instance = new DelayCalculatorSingleton();
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return instance;
    }

    public void testLinksLatency() {
//        Predicate<Link> ignoreControlLinks = link -> !link.src().deviceId().equals(CONTROLLER_HOST.location().deviceId()) && !link.dst().deviceId().equals(CONTROLLER_HOST.location().deviceId());
        List<Link> links = StreamSupport.stream(Services.linkService.getLinks().spliterator(), false)
//                .filter(ignoreControlLinks)
                .collect(Collectors.toList());
        testLinksLatency(links);
    }

    public void testLinksLatency(Iterable<Link> links) {
        try {
            Services.getExecutor(ThreadsEnum.DELAY_CALCULATOR).submit(() -> handleLinks(links));
        } catch (Exception e) {
            log.error("", e);
        }
    }


    private void handleLinks(Iterable<Link> links) {
        links.forEach(link -> {
            pingSwitch(link.src());
            pingSwitch(link.dst());
            sendProbe(link);
        });
    }

    private void pingSwitch(ConnectPoint switchConnPoint) {
        if (controlLinkLatencies.containsKey(switchConnPoint.deviceId())) {
            return;
        }
        byte icmpType = (byte) (new Random().nextInt(80) + 10);
        Path pathToSwitch = getShortestPath(getNearestControllerHost(switchConnPoint.elementId()).id(), switchConnPoint.elementId());
        installHostToSwitchRules(pathToSwitch, icmpType);
        runScapy(icmpType, switchConnPoint, switchConnPoint);
    }

    private Path getShortestPath(ElementId src, ElementId dst) {
        Collection<Path> paths = Services.pathService.getKShortestPaths(src, dst).collect(Collectors.toCollection(LinkedHashSet::new));
        if (paths.isEmpty()) {
            throw new RuntimeException("No path found to link");  // I mean how come?
        }
        return paths.iterator().next();
    }


    private void sendProbe(Link link) {
        byte icmpType = (byte) (new Random().nextInt(100) + 10);
        Path pathToSrcSwitch = getShortestPath(getNearestControllerHost(link.src().deviceId()).id(), link.src().deviceId());
        List<Link> links = new LinkedList<>(pathToSrcSwitch.links());
        links.add(link);
        Path dstToControllerPath = getShortestPath(link.dst().deviceId(), getNearestControllerHost(link.dst().deviceId()).id());
        links.addAll(dstToControllerPath.links());
        Path probePath = new DefaultPath(ProviderId.NONE, links, ScalarWeight.toWeight(0));
        installProbePacketRules(probePath, icmpType);
        runScapy(icmpType, link.src(), link.dst());
    }

    private void installProbePacketRules(Path probePath, byte icmpCode) {
        List<FlowRule> flowRules = new LinkedList<>();
        List<Link> links = probePath.links();
        PortNumber outPort;
        DeviceId deviceId;
        PortNumber inPort = links.get(0).dst().port();
        for (int i = 1; i < links.size(); i++) {
            outPort = links.get(i).src().port();
            deviceId = links.get(i).src().deviceId();
            flowRules.add(getFlowEntry(deviceId, outPort, inPort, icmpCode));
            inPort = links.get(i).dst().port();
        }
        installRulesToSwitches(flowRules);
    }

    private void installHostToSwitchRules(Path path, byte icmpCode) {
        List<FlowRule> flowRules = new LinkedList<>();
        List<Link> links = path.links();
        PortNumber outPort;
        DeviceId deviceId;
        PortNumber inPort = links.get(0).dst().port();
        for (int i = 1; i < links.size(); i++) {
            outPort = links.get(i).src().port();
            deviceId = links.get(i).src().deviceId();
            flowRules.add(getFlowEntry(deviceId, outPort, inPort, icmpCode));
            inPort = links.get(i).dst().port();
        }
        for (int i = links.size() - 1; i >= 0; i--) {
            outPort = links.get(i).dst().port();
            deviceId = links.get(i).dst().deviceId();
            flowRules.add(getFlowEntry(deviceId, outPort, inPort, icmpCode));
            inPort = links.get(i).src().port();
        }
        installRulesToSwitches(flowRules);
    }

    private void installRulesToSwitches(List<FlowRule> flowRules) {
        Services.flowRuleService.applyFlowRules(flowRules.toArray(new FlowRule[0]));
        FlowEntry fe = Services.flowRuleService.getFlowEntry(flowRules.get(flowRules.size() - 1));
        while (fe == null || fe.state() != FlowEntry.FlowEntryState.ADDED) {
            try {
                Thread.sleep(100);
                fe = Services.flowRuleService.getFlowEntry(flowRules.get(flowRules.size() - 1));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void runScapy(byte icmpType, ConnectPoint srcDeviceConnPoint, ConnectPoint dstDeviceConnPoint) {
        try {

            String home = System.getProperty("user.home");
//            String password = "OH@md@n@1997";
            boolean isPing = srcDeviceConnPoint.equals(dstDeviceConnPoint);
            String[] allCmd = {
                    "/bin/sh",
                    "-c",
                    String.format("echo neo | sudo -S python3 %s/all_scapy.py %s %s %s", home, String.valueOf(icmpType), srcDeviceConnPoint.deviceId(), dstDeviceConnPoint.deviceId())
            };


            Process run = Runtime.getRuntime().exec(allCmd);


            String stderr = IOUtils.toString(run.getErrorStream(), Charset.defaultCharset());
            String stdout = IOUtils.toString(run.getInputStream(), Charset.defaultCharset());

            if (!StringUtils.isBlank(stderr)) {
                log.error("Scapy Error: {}", stderr);
            }

            if (isPing) {
                controlLinkLatencies.put(srcDeviceConnPoint.deviceId(), Double.parseDouble(stdout));
                //log.debug("Delay for switch {} is {}ms", srcDeviceConnPoint.deviceId(), stdout);
            } else {
                double delay = Double.parseDouble(stdout) - (controlLinkLatencies.get(srcDeviceConnPoint.deviceId()) + controlLinkLatencies.get(dstDeviceConnPoint.deviceId())) / 2;
                LinksInformationDatabase.updateLinkLatestDelay(Services.linkService.getLink(srcDeviceConnPoint, dstDeviceConnPoint), delay);
                log.debug("Delay between switch {} and {} is {}ms", srcDeviceConnPoint, dstDeviceConnPoint, delay);
            }

        } catch (Exception e) {
            log.error("", e);
        }
    }


    private FlowRule getFlowEntry(DeviceId dId, PortNumber outPort, PortNumber inPort, byte icmpType) {

        if (inPort.equals(outPort)) {
            outPort = PortNumber.IN_PORT;
        }

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort)
                .build();

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_ICMP)
                .matchIcmpType(icmpType)
                .matchInPort(inPort)
                .build();


        return DefaultFlowRule.builder()
                .withTreatment(treatment)
                .withSelector(selector)
                .withPriority(3001)
                .forDevice(dId)
                .fromApp(DELAY_CALCULATOR_APP_ID)
                .makeTemporary(Util.POLL_FREQ)
                .build();
    }

    private Host getNearestControllerHost(ElementId id) {
        return Services.hostService.getHostsByIp(CONTROLLER_HOST_IP).stream().filter(h -> h.location().deviceId().equals(id))
                .findFirst().orElseThrow(() -> new RuntimeException(String.format("No host connected to %s", id)));
    }
}