package edu.unr.hpclab.flowcontrol.app;

import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;
import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.PathCalculator;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static edu.unr.hpclab.flowcontrol.app.Services.appId;
import static edu.unr.hpclab.flowcontrol.app.Services.flowObjectiveService;
import static org.onlab.packet.Ethernet.TYPE_IPV4;

public class PathFinderAndRuleInstaller {
    static Logger log = LoggerFactory.getLogger(CongestionHelper.class);

    public static Path applyAndGetPath(SrcDstPair srcDstPair) {
        return applyAndGetPath(srcDstPair, 0);
    }

    public static Path applyAndGetPath(SrcDstPair srcDstPair, long requestedRate) {
        return applyAndGetPath(srcDstPair);
    }

    public static MyPath applyAndGetPath(SrcDstTrafficInfo srcDstTrafficInfo) {
        Supplier<RuntimeException> exceptionSupplier = () -> new RuntimeException(String.format("No path found for %s", srcDstTrafficInfo.getSrcDstPair()));
        List<MyPath> paths;
        if (srcDstTrafficInfo.getRequestedRate() > 0) {
            if (srcDstTrafficInfo.getRequestedDelay() > 0) {
                paths = Optional.ofNullable(PathCalculator.getPathsSortedByRateDelayFit(srcDstTrafficInfo))
                        .orElseThrow(exceptionSupplier);
            } else {
                paths = Optional.ofNullable(PathCalculator.getPathsSortedByRateFit(srcDstTrafficInfo))
                        .orElseThrow(exceptionSupplier);
            }
        } else {
            paths = Optional.ofNullable(PathCalculator.getPathsByMaxSharedAvailableCapacity(srcDstTrafficInfo))
                    .orElseThrow(exceptionSupplier);
        }
        MyPath path = paths.get(0);
        installPathRules(srcDstTrafficInfo.getSrcDstPair(), path);
        return path;
    }

    public static void installPathRules(SrcDstPair srcDstPair, Path path) {
        List<Link> links = path.links();
        PortNumber outPort;
        DeviceId deviceId;
        PortNumber inPort = links.get(0).dst().port();
        for (int i = 1; i < links.size(); i++) {
            outPort = links.get(i).src().port();
            deviceId = links.get(i).src().deviceId();
            addFlowEntry(deviceId, srcDstPair, outPort, inPort);
            inPort = links.get(i).dst().port();
        }
    }

    private static void addFlowEntry(DeviceId dId, SrcDstPair srcDstPair, PortNumber outPort, PortNumber inPort) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort)
                .build();

        TrafficSelector tf = DefaultTrafficSelector.builder()
                .matchEthSrc(srcDstPair.getSrcMac())
                .matchEthDst(srcDstPair.getDstMac())
                .matchEthType(TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                .matchTcpSrc(TpPort.tpPort(srcDstPair.getSrcPort()))
                .matchTcpDst(TpPort.tpPort(srcDstPair.getDstPort()))
                .matchInPort(inPort)
                .build();

        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withTreatment(treatment)
                .withSelector(tf)
                .withPriority(10)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makeTemporary(Util.POLL_FREQ)
                .add();

        flowObjectiveService.forward(dId, forwardingObjective);
    }
}
