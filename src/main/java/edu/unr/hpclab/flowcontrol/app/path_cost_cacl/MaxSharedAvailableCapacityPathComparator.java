package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import java.util.Comparator;
import java.util.function.UnaryOperator;

public class MaxSharedAvailableCapacityPathComparator extends PathCalculator implements Comparator<MyPath> {
    private static final Comparator<MyPath> INSTANCE = new MaxSharedAvailableCapacityPathComparator();

    public static Comparator<MyPath> instance() {
        return INSTANCE;
    }

    private MaxSharedAvailableCapacityPathComparator() {
    }

    @Override
    public int compare(MyPath path1, MyPath path2) {
        double available1 = path1.getMaxAvailableSharedRate();
        double available2 = path2.getMaxAvailableSharedRate();
        return Double.compare(available1, available2) * -1; // First goes the largest available bandwidth
    }
}
