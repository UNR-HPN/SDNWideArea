package edu.unr.hpclab.flowcontrol.app.path_cost_cacl;

import java.util.Comparator;

public class PathDelayComparator extends PathCalculator implements Comparator<MyPath> {
    public static final double COMPARING_FACTOR = 1.3;

    private final double requestedDelay;

    public PathDelayComparator(double requestedDelay) {
        this.requestedDelay = requestedDelay;
    }

    public PathDelayComparator() {
        this.requestedDelay = 0;
    }

    @Override
    public int compare(MyPath path1, MyPath path2) {
        double delay1 = path1.getDelay();
        double delay2 = path2.getDelay();
        if (requestedDelay == 0) {
            return Double.compare(delay1, delay2);
        }
        if (delay1 <= requestedDelay && delay2 >= requestedDelay) {
            return -1;
        } else if (delay1 >= requestedDelay && delay2 <= requestedDelay) {
            return 1;
        } else {
            return Double.compare(delay1, delay2);
        }
    }
}
