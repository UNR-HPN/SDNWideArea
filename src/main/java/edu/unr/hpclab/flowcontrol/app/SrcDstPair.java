package edu.unr.hpclab.flowcontrol.app;

import org.onlab.packet.MacAddress;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

public class SrcDstPair {
    private final MacAddress srcMac;
    private final MacAddress dstMac;
    private final int srcPort;
    private final int dstPort;


    public SrcDstPair(MacAddress srcMac, MacAddress dstMac, int srcPort, int dstPort) {
        this.srcMac = srcMac;
        this.dstMac = dstMac;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SrcDstPair srcDstPair = (SrcDstPair) o;
        return Objects.equals(srcMac, srcDstPair.srcMac) && Objects.equals(dstMac, srcDstPair.dstMac) && Objects.equals(dstPort, srcDstPair.dstPort) && Objects.equals(srcPort, srcDstPair.srcPort);
    }

    public int getSrcPort() {
        return srcPort;
    }

    public int getDstPort() {
        return dstPort;
    }

    public MacAddress getSrcMac() {
        return srcMac;
    }

    public MacAddress getDstMac() {
        return dstMac;
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcMac, dstMac, srcPort, dstPort);
    }

    public SrcDstPair reversed(){
        return new SrcDstPair(dstMac, srcMac, dstPort, srcPort);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("src->dst:", srcMac + "->" + dstMac)
                .add("src port:", getSrcPort())
                .add("dst port:", getDstPort())
                .toString();
    }
}
