package com.github.redhatqe.polarizer.verticles.proto;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;

/**
 * This is the base class for transporting binary data across either the websocket or vertx event bus
 *
 * In this case, the collected data is just a lump of bytes, and it will be decoded into an object of this type
 */
public class BinaryMessage {
    @JsonProperty(required = true)
    protected String op;
    @JsonProperty(required = true)
    protected String type;
    @JsonProperty(required = true)
    protected Buffer data;
    @JsonProperty
    protected String tag;
    @JsonProperty
    protected Boolean ack;

    public BinaryMessage() {

    }

    public BinaryMessage(String op, String t, Buffer d, String tag, Boolean ack) {
        this.op = op;
        this.type = t;
        this.tag = tag;
        this.data = d;
        this.ack = ack;
    }

    public JsonObject makeReplyMessage(Buffer reply, Boolean ackNak) {
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

    public Buffer getData() {
        return data;
    }

    public void setData(Buffer data) {
        this.data = data;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Boolean getAck() {
        return ack;
    }

    public void setAck(Boolean ack) {
        this.ack = ack;
    }
}
