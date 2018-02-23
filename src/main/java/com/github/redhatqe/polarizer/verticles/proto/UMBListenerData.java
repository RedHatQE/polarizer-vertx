package com.github.redhatqe.polarizer.verticles.proto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UMBListenerData {
    @JsonProperty(required = true)
    private String topic;
    @JsonProperty(required = true)
    private String selector;
    @JsonProperty(value = "bus-address", required = true)
    private String busAddress;
    @JsonProperty(required = true)
    private String action;
    @JsonProperty(required = true)
    private String tag;

    public String clientAddress;


    public UMBListenerData() {

    }

    public UMBListenerData(String topic, String sel, String bus, String action, String tag, String caddress) {
        this.topic = topic;
        this.selector = sel;
        this.busAddress = bus;
        this.action = action;
        this.tag = tag;
        this.clientAddress = caddress;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getBusAddress() {
        return busAddress;
    }

    public void setBusAddress(String busAddress) {
        this.busAddress = busAddress;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
