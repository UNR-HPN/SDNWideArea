package edu.unr.hpclab.flowcontrol.app;

import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.PathCalculator;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;

import java.util.LinkedList;
import java.util.List;

import static org.onlab.packet.Ethernet.TYPE_IPV4;

public class PathFinderAndRuleInstaller {
    private final int priority;
    private final int liveTime;

    public PathFinderAndRuleInstaller(int priority, int liveTime) {
        this.priority = priority % 3000;
        this.liveTime = liveTime;
    }

    public PathFinderAndRuleInstaller() {
        this(20, Util.POLL_FREQ);
    }

    public PathFinderAndRuleInstaller(int priority) {
        this(priority, Util.POLL_FREQ);
    }

    public Path applyAndGetPath(SrcDstPair srcDstPair) {
        return applyAndGetPath(srcDstPair, 0);
    }

    public Path applyAndGetPath(SrcDstPair srcDstPair, long requestedRate) {
        return applyAndGetPath(srcDstPair);
    }

    public MyPath applyAndGetPath(SrcDstTrafficInfo srcDstTrafficInfo) {
        List<MyPath> paths;
        if (srcDstTrafficInfo.getRequestedRate() > 0) {
            if (srcDstTrafficInfo.getRequestedDelay() > 0) {
                paths = PathCalculator.getPathsSortedByRateDelayFit(srcDstTrafficInfo);
            } else {
                paths = PathCalculator.getPathsSortedByRateFit(srcDstTrafficInfo);
            }
        } else {
            paths = PathCalculator.getPathsByMaxSharedAvailableCapacity(srcDstTrafficInfo);
        }
        MyPath path = paths.get(0);
        installPathRules(srcDstTrafficInfo.getSrcDstPair(), path);
        return path;
    }

    public void installPathRules(SrcDstPair srcDstPair, Path path) {
        List<FlowRule> rules = new LinkedList<>();
        List<Link> links = path.links();
        PortNumber outPort;
        DeviceId deviceId;
        PortNumber inPort = links.get(0).dst().port();
        for (int i = 1; i < links.size(); i++) {
            outPort = links.get(i).src().port();
            deviceId = links.get(i).src().deviceId();
            rules.add(getFlowEntry(deviceId, srcDstPair, outPort, inPort));
            inPort = links.get(i).dst().port();
        }
        Services.flowRuleService.applyFlowRules(rules.toArray(new FlowRule[0]));
    }


    private FlowRule getFlowEntry(DeviceId dId, SrcDstPair srcDstPair, PortNumber outPort, PortNumber inPort) {

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort)
                .build();

        TrafficSelector.Builder tf = DefaultTrafficSelector.builder()
                .matchEthType(TYPE_IPV4)
                .matchEthSrc(srcDstPair.getSrcMac())
                .matchEthDst(srcDstPair.getDstMac())
                .matchInPort(inPort);

        if (srcDstPair.getProtocol() == IPv4.PROTOCOL_TCP) {
            tf.matchIPProtocol(IPv4.PROTOCOL_TCP);
            tf.matchTcpSrc(TpPort.tpPort(srcDstPair.getSrcPort()));
            tf.matchTcpDst(TpPort.tpPort(srcDstPair.getDstPort()));
        } else if (srcDstPair.getProtocol() == IPv4.PROTOCOL_UDP) {
            tf.matchIPProtocol(IPv4.PROTOCOL_UDP);
            tf.matchUdpSrc(TpPort.tpPort(srcDstPair.getSrcPort()));
            tf.matchUdpDst(TpPort.tpPort(srcDstPair.getDstPort()));
        }

        return DefaultFlowRule.builder()
                .withTreatment(treatment)
                .withSelector(tf.build())
                .forDevice(dId)
                .withPriority(priority)
                .fromApp(Services.appId)
                .makeTemporary(liveTime)
                .build();
    }
}
