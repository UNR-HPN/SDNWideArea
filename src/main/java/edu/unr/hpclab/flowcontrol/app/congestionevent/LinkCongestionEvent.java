package edu.unr.hpclab.flowcontrol.app.congestionevent;


import org.onosproject.event.AbstractEvent;
import org.onosproject.net.Link;

public class LinkCongestionEvent extends AbstractEvent<LinkCongestionEvent.Type, Link> {

    private long rate;

    public LinkCongestionEvent(Type type, Link link) {
        super(type, link);
    }

    public LinkCongestionEvent(Type type, Link link, long rate) {
        super(type, link);
        this.rate = rate;
    }

    public long getRate() {
        return rate;
    }


    enum Reason {
        LINK_BANDWIDTH,
        MULTIPLE_INGRESS_TRAFFIC,
    }

    public enum Type {
        HEAVILY_CONGESTED(0.8),
        CONGESTED(0.5),
        SLIGHTLY_CONGESTED(0.3);

        double congestionPercentage;

        Type(double v) {
            congestionPercentage = v;
        }
    }
}