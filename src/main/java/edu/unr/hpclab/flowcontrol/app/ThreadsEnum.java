package edu.unr.hpclab.flowcontrol.app;

public enum ThreadsEnum {
    SOLUTION_FINDER(1, 2),
    DELAY_CALCULATOR(1, 1),
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
