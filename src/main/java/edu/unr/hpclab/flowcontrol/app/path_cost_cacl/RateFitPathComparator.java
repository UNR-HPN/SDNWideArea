package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import java.util.Comparator;

public class RateFitPathComparator extends PathCalculator implements Comparator<MyPath> {
    private final long requestedRate;

    public RateFitPathComparator(long requestedRate) {
        this.requestedRate = requestedRate;
    }

    @Override
    public int compare(MyPath path1, MyPath path2) {
        double available1 = path1.getAvailableRate();
        double available2 = path2.getAvailableRate();
        if (requestedRate <= available1 && requestedRate > available2) {
            return -1;
        } else if (requestedRate > available1 && requestedRate <= available2) {
            return 1;
        }
        else if (requestedRate <= available1 && requestedRate <= available2) {
            return 0;   // Leave this to be decided by the next comparator
        }
        else {
            return Double.compare(available1, available2) * -1; // First goes the largest available bandwidth
        }
    }
}
