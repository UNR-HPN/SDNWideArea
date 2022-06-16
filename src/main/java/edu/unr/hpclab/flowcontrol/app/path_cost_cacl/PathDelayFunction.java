package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import edu.unr.hpclab.flowcontrol.app.LinksInformationDatabase;

import java.util.function.UnaryOperator;

public class PathDelayFunction extends PathCalculator implements UnaryOperator<MyPath> {
    private static final UnaryOperator<MyPath> INSTANCE = new PathDelayFunction();

    public static UnaryOperator<MyPath> instance() {
        return INSTANCE;
    }

    private PathDelayFunction() {}

    @Override
    public MyPath apply(MyPath myPath) {
        double delay = myPath.links().stream().mapToDouble(LinksInformationDatabase::getLatestLinkDelay).sum();
        myPath.setDelay(delay);
        return myPath;
    }
}
