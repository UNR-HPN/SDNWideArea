package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import org.onosproject.net.Path;

import java.util.Comparator;

public class ActiveFlowPathComparator extends PathCalculator implements Comparator<Path> {
    public static final double COMPARING_FACTOR = 1.5;

    @Override
    public int compare(Path path1, Path path2) {
        int flowsNo1 = getCurrentRatesOfActiveFlows(path1).getLeft();
        int flowsNo2 = getCurrentRatesOfActiveFlows(path2).getLeft();
        return Integer.compare(flowsNo1, flowsNo2);
//        if (flowsNo1 <= flowsNo2) {
//            if (flowsNo2 * 1.0 / Math.max(flowsNo1, 1) >= COMPARING_FACTOR) {
//                return -1;
//            } else {
//                return 0;
//            }
//        } else {
//            return 1;
//        }
    }
}
