package com.github.redhatqe.polarizer.verticles.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;

import javax.xml.soap.Text;


/**
 * Message exchange format for both eventbus and websockets using text based (JSON) formats
 *
 * - op: Defines one of the possible opcodes
 * - type: Defines the type of data coming across
 * - data: The body content
 */
public class TextMessage {
    @JsonProperty(required = true)
    protected String op;
    @JsonProperty(required = true)
    protected String type;
    @JsonProperty(required = true)
    protected String data;
    @JsonProperty
    protected String tag;
    @JsonProperty
    protected Boolean ack;

    public TextMessage() {

    }

    public TextMessage(String op, String type, String data, String tag, Boolean ack) {
        this.op = op;
        this.type = type;
        this.tag = tag;
        this.data = data;
        this.ack = ack;
    }

    public JsonObject makeReplyMessage(String reply, Boolean ackNak) {
        JsonObject msg = new JsonObject();
        msg.put("op", this.getOp() + "-response");
        msg.put("type", this.getType());
        msg.put("ack", ackNak != null && ackNak);
        msg.put("tag", this.getTag());
        msg.put("data", reply);

        return msg;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Boolean getAck() {
        return ack;
    }

    public void setAck(Boolean ack) {
        this.ack = ack;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
