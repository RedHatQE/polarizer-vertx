package com.github.redhatqe.polarizer.verticles.proto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class UMBListenerData {
    @JsonProperty(required = true)
    private String topic;
    @JsonProperty(required = true)
    private String selector;
    @JsonProperty(value = "bus-address", required = true)
    private String busAddress;    // address on event bus
    @JsonProperty(required = true)
    private String action;
    @JsonProperty(required = true)
    private String tag;

    public String clientAddress;  // what service to send to


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

    public enum UMBAction {
        START, STOP
    }

    public static UMBListenerData makeDefault(UMBAction action, String sel, String tag) {
        UUID uuid = UUID.randomUUID();
        String topic = String.format("Consumer.client-polarize.%s.VirtualTopic.qe.ci.>", uuid.toString());
        String busAddress = "rhsmqe.messages";
        String act = action.toString().toLowerCase();
        return new UMBListenerData(topic, sel, busAddress, act, tag, "null");
    }
}
