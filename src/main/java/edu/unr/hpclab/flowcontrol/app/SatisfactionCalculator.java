package edu.unr.hpclab.flowcontrol.app;

import edu.unr.hpclab.flowcontrol.app.path_cost_cacl.MyPath;

public class SatisfactionCalculator {
    public static double calculate(MyPath path, SrcDstTrafficInfo srcDstTrafficInfo) {
        long requestedRate = srcDstTrafficInfo.getRequestedRate();
        double requestedDelay = srcDstTrafficInfo.getRequestedDelay();
        long availableRate = path.getAvailableRate();
        double pathDelay = path.getDelay();
        double rateSat = availableRate * 1.0 / requestedRate;
        double delaySat =  requestedDelay / pathDelay;
        return rateSat * 0.7 + delaySat * 0.3;
    }
}
