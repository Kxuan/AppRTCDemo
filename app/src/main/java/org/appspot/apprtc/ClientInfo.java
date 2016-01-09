package org.appspot.apprtc;

public class ClientInfo {
    private long clientId;
    private String device;

    public ClientInfo(long clientId, String device) {
        this.clientId = clientId;
        this.device = device;
    }

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    @Override
    public String toString() {
        return String.format("%d(%s)", clientId, device);
    }
}
