package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import edu.unr.hpclab.flowcontrol.app.LinksInformationDatabase;
import org.apache.commons.lang3.tuple.Pair;
import org.onosproject.net.Link;

import java.util.function.UnaryOperator;

public class SharedCapacityPathWeightFunction extends PathCalculator implements UnaryOperator<MyPath> {
    @Override
    public MyPath apply(MyPath path) {
        Link lowestBwLink = getLowest_HighestLinkBandwidth(path, "lowest");
        Pair<Integer, Long> flowsToTotalRate = getCurrentRatesOfActiveFlows(lowestBwLink);
        long lowestBw = LinksInformationDatabase.getLinkEstimatedBandwidth(lowestBwLink);
        int noFlows = Math.max(flowsToTotalRate.getLeft(), 1);
        long shareRate = lowestBw / noFlows;
        path.setSharedRate(shareRate);
        return path;
    }
}
