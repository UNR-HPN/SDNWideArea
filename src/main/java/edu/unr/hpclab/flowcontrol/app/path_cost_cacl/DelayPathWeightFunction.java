package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import edu.unr.hpclab.flowcontrol.app.LinksInformationDatabase;

import java.util.function.UnaryOperator;

public class DelayPathWeightFunction extends PathCalculator implements UnaryOperator<MyPath> {
    private static final UnaryOperator<MyPath> INSTANCE = new AvailableCapacityPathWeightFunction();

    public static UnaryOperator<MyPath> instance() {
        return INSTANCE;
    }

    @Override
    public MyPath apply(MyPath path) {
        double delay = path.links().stream().mapToDouble(LinksInformationDatabase::getLatestLinkDelay).sum();
        path.setDelay(delay);
        return path;
    }
}
