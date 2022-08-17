package edu.unr.hpclab.flowcontrol.app;

import java.util.concurrent.ThreadPoolExecutor;

public enum ThreadsEnum {
    SOLUTION_FINDER(1, 1),
    DELAY_CALCULATOR(1, 1), // Calculate the link delay concurrently
    NEW_PATH_FINDER(10, 10),
    ;
    private final int poolSize, qSize;

    ThreadsEnum(int poolSize, int qSize) {
        this.poolSize = poolSize;
        this.qSize = qSize;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getQSize() {
        return qSize;
    }

}
