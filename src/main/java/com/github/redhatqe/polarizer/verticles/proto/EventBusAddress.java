package com.github.redhatqe.polarizer.verticles.proto;

public enum EventBusAddress {
    UMB_MESSAGES_START,
    UMB_MESSAGES_STOP;

    interface ToString {
        default String stringify() {
            return this.toString().toLowerCase().replace('_', '.');
        }
    }
}
