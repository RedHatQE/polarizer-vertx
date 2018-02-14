package com.github.redhatqe.polarizer.verticles.tests;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.redhatqe.polarizer.verticles.http.data.UMBListenerData;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpClient;

import java.util.UUID;

public class WebSocketClient extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        HttpClient client = vertx.createHttpClient();
        System.out.println("In WebSocketClient verticle");

        client.websocket(9000, "localhost", "/umb/start", websocket -> {
            UMBListenerData data = new UMBListenerData();
            data.setAction("start");
            data.setTag("rhsmqe");
            data.setBusAddress("rhsmqe.messages");
            data.setSelector("");
            UUID rand = UUID.randomUUID();
            data.setTopic(String.format("Consumer.client-polarize.%s.VirtualTopic.qe.ci.>", rand.toString()));
            ObjectMapper mapper = new ObjectMapper();

            String request = null;
            try {
                request = mapper.writeValueAsString(data);
                System.out.println(request);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            if (request != null) {
                System.out.println("Sending json request");
                websocket.writeFinalTextFrame(request);
            }
            websocket.handler(d -> {
                System.out.println("Received data " + d.toString("ISO-8859-1"));
                //client.close();
            });
            websocket.writeBinaryMessage(Buffer.buffer("Hello world"));
        });
    }
}
