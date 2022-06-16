package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import edu.unr.hpclab.flowcontrol.app.LinksInformationDatabase;
import org.apache.commons.lang3.tuple.Pair;
import org.onosproject.net.Link;

import java.util.function.UnaryOperator;

public class AvailableCapacityPathWeightFunction extends PathCalculator implements UnaryOperator<MyPath> {
    private static final UnaryOperator<MyPath> INSTANCE = new AvailableCapacityPathWeightFunction();

    public static UnaryOperator<MyPath> instance() {
        return INSTANCE;
    }

    private AvailableCapacityPathWeightFunction() {
    }

    @Override
    public MyPath apply(MyPath path) {
        Link lowestBwLink = getLowest_HighestLinkBandwidth(path, "lowest");
        Pair<Integer, Long> flowsToTotalRate = getCurrentRatesOfActiveFlows(lowestBwLink);
        long lowestBw = LinksInformationDatabase.getLinkEstimatedBandwidth(lowestBwLink);
        long totalRate = flowsToTotalRate.getRight();
        long availableCapacity = Math.max(lowestBw - totalRate, 0);
        path.setAvailableRate(availableCapacity);
        path.setBottleneckLink(lowestBwLink);
        return path;
    }
}
