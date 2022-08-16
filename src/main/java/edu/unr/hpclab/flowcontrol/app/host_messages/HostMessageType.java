package edu.unr.hpclab.flowcontrol.app.host_messages;

public enum HostMessageType {
    RATE_REQUEST(Integer.class),
    DELAY_REQUEST(Double.class),
    UNKNOWN(null);

    final Class<?> valueType;

    HostMessageType(Class<?> valueType) {
        this.valueType = valueType;
    }

    public Number parse(String raw) {
        if (this.valueType == Integer.class) {
            return Integer.parseInt(raw);
        }
        if (this.valueType == Double.class) {
            return Double.parseDouble(raw);
        } else {
            return 0;
        }
    }

    public Object getType() {
        return valueType;
    }
}
