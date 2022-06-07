package edu.unr.hpclab.flowcontrol.app;

import org.onlab.graph.ScalarWeight;
import org.onlab.graph.Weight;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.topology.DefaultTopologyEdge;
import org.onosproject.net.topology.LinkWeigher;
import org.onosproject.net.topology.TopologyEdge;


public class MyLinkWeigher implements LinkWeigher {
    private int highestHopCount;
    private long highestUtilization;
    private long highestBandwidth;
    private long incomingRate;

    public MyLinkWeigher(long incomingRate) {
        this.incomingRate = incomingRate;
    }

    public MyLinkWeigher() {
    }

    public static Weight recalculatePathWeight(Path path) {
        MyLinkWeigher weigher = new MyLinkWeigher();
        Weight weight = ScalarWeight.toWeight(0.0);
        for (Link link : path.links()) {
            TopologyEdge te = new DefaultTopologyEdge(null, null, link);
            weight = weight.merge(weigher.weight(te));
        }
        return weight;
    }

    public int getHighestHopCount() {
        return highestHopCount;
    }

    public void setHighestHopCount(int highestHopCount) {
        this.highestHopCount = highestHopCount;
    }

    public long getHighestUtilization() {
        return highestUtilization;
    }

    public void setHighestUtilization(long highestUtilization) {
        this.highestUtilization = highestUtilization;
    }

    public long getHighestBandwidth() {
        return highestBandwidth;
    }

    public void setHighestBandwidth(long highestBandwidth) {
        this.highestBandwidth = highestBandwidth;
    }

    @Override
    public Weight weight(TopologyEdge edge) {
        Link link = edge.link();
        if (link.type().equals(Link.Type.EDGE)) {
            return ScalarWeight.toWeight(0);
        }
        double hopCount = 1;
        double scaledBW = (3000.0 / Util.BpsToMbps(LinksInformationDatabase.getLinkEstimatedBandwidth(link)));
        double util = getUtilization(link) * 10;
        return ScalarWeight.toWeight(hopCount + util + scaledBW);
    }

    private double getUtilization(Link link) {
        double util;
        long bw = LinksInformationDatabase.getLinkEstimatedBandwidth(link);
        double linkUtil = LinksInformationDatabase.getLinkUtilization(link);
        if (this.incomingRate != 0 && linkUtil != 0) {
            util = (linkUtil + (incomingRate * 1.0 / bw));
        } else {
            util = Math.max(linkUtil, this.incomingRate * 1.0 / bw);
        }
        return util;
    }


    @Override
    public Weight getInitialWeight() {
        return new ScalarWeight(0.0D);
    }

    @Override
    public Weight getNonViableWeight() {
        return ScalarWeight.NON_VIABLE_WEIGHT;
    }

}
