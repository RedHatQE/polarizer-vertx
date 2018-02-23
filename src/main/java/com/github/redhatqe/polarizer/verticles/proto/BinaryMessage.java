package com.github.redhatqe.polarizer.verticles.proto;


/**
 * This is the base class for transporting binary data across either the websocket or vertx event bus
 *
 * In this case, the collected data is just a lump of bytes, and it will be decoded into an object of this type
 */
public class BinaryMessage {
    public int op;
    public boolean ack;

}
