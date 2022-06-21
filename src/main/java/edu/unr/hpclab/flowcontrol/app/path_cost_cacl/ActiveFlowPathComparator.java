package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import java.util.Comparator;

public class ActiveFlowPathComparator extends PathCalculator implements Comparator<MyPath> {
    private static final Comparator<MyPath> INSTANCE = new ActiveFlowPathComparator();

    public static Comparator<MyPath> instance() {
        return INSTANCE;
    }
    @Override
    public int compare(MyPath path1, MyPath path2) {
        int flowsNo1 = getCurrentRatesOfActiveFlows(path1).getLeft();
        int flowsNo2 = getCurrentRatesOfActiveFlows(path2).getLeft();
        return Integer.compare(flowsNo1, flowsNo2);
    }
}
