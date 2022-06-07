package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import java.util.function.UnaryOperator;

public class MaxSharedAvailableCapacityFunction extends PathCalculator implements UnaryOperator<MyPath> {
    private static final UnaryOperator<MyPath> INSTANCE = new MaxSharedAvailableCapacityFunction();

    public static UnaryOperator<MyPath> instance() {
        return INSTANCE;
    }

    @Override
    public MyPath apply(MyPath path) {
        path = new SharedCapacityPathWeightFunction().apply(path);
        path = new AvailableCapacityPathWeightFunction().apply(path);
        long sharedCapacity = path.getSharedRate();
        long availableCapacity = path.getAvailableRate();
        long max = Math.max(sharedCapacity, availableCapacity);
        path.setMaxAvailableSharedRate(max);
        return path;
    }
}
