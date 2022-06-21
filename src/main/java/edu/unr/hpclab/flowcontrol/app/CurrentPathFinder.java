package edu.unr.hpclab.flowcontrol.app;

import com.google.common.collect.ImmutableSet;
import org.onlab.packet.MacAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CurrentPathFinder {
    private static final Services services = Services.getInstance();

    public static List<Link> getCurrentPath(SrcDstPair sd) {
        List<FlowEntry> associatedFEList = findAssociatedFlowEntryList(sd);

        Host srcHost = services.hostService.getHost(HostId.hostId(sd.getSrcMac()));
        Host dstHost = services.hostService.getHost(HostId.hostId(sd.getDstMac()));
        DeviceId srcId = srcHost.location().deviceId();
        DeviceId dstId = dstHost.location().deviceId();

        List<Link> linkPath = new ArrayList<>();
        DeviceId dId = srcId;
        while (!dstId.equals(dId)) {
            FlowEntry fe = findFlowEntry(associatedFEList, dId);
            if (fe == null) {
                return null; // no valid path
            }
            PortNumber outPortNum = null;
            for (Instruction ins : fe.treatment().immediate()) {
                if (ins.type().equals(Instruction.Type.OUTPUT)) {
                    outPortNum = ((Instructions.OutputInstruction) ins).port();
                    break;
                }
            }
            Set<Link> egressLinkSet = services.linkService.getDeviceEgressLinks(dId);
            for (Link l : egressLinkSet) {
                if (l.src().port().equals(outPortNum)) {
                    dId = l.dst().deviceId();
                    linkPath.add(l);
                    break;
                }
            }

        }

        return linkPath;
    }

    private static List<FlowEntry> findAssociatedFlowEntryList(SrcDstPair sd) {
        Set<FlowEntry> feSet = getAllCurrentFlowEntries();
        List<FlowEntry> associatedFEList = new ArrayList<>();
        for (FlowEntry fe : feSet) {
            MacAddress src = null, dst = null;
            for (Criterion cr : fe.selector().criteria()) {
                if (cr.type() == Criterion.Type.ETH_DST) {
                    dst = ((EthCriterion) cr).mac();
                } else if (cr.type() == Criterion.Type.ETH_SRC) {
                    src = ((EthCriterion) cr).mac();
                }
            }
            if (src == null || dst == null) {
                continue;
            }
            if (sd.getSrcMac().equals(src) && sd.getDstMac().equals(dst)) {
                associatedFEList.add(fe);
            }
        }
        return associatedFEList;
    }

    public static Set<FlowEntry> getAllCurrentFlowEntries() {
        Set<FlowEntry> flowEntries = new LinkedHashSet<>();
        for (Link l : services.linkService.getLinks()) {
            flowEntries.addAll(getFlowEntries(l.src()));
        }
        return flowEntries;
    }

    public static Set<FlowEntry> getFlowEntries(ConnectPoint egress) {
        ImmutableSet.Builder<FlowEntry> builder = ImmutableSet.builder();
        services.flowRuleService.getFlowEntries(egress.deviceId()).forEach(r -> {
            r.treatment().allInstructions().forEach(i -> {
                if (i.type() == Instruction.Type.OUTPUT) {
                    if (((Instructions.OutputInstruction) i).port().equals(egress.port())) {
                        builder.add(r);
                    }
                }
            });
        });
        return builder.build();
    }

    private static FlowEntry findFlowEntry(List<FlowEntry> feList, DeviceId id) {
        for (FlowEntry fe : feList) {
            if (fe.deviceId().equals(id)) {
                return fe;
            }
        }
        return null;
    }
}
