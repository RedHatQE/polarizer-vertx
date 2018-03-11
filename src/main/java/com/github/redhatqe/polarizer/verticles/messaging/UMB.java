package com.github.redhatqe.polarizer.verticles.messaging;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.messagebus.CIBusListener;
import com.github.redhatqe.polarizer.messagebus.DefaultResult;
import com.github.redhatqe.polarizer.messagebus.MessageHandler;
import com.github.redhatqe.polarizer.messagebus.MessageResult;
import com.github.redhatqe.polarizer.messagebus.config.BrokerConfig;
import com.github.redhatqe.polarizer.messagebus.utils.Tuple;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.verticles.proto.UMBListenerData;
import io.reactivex.disposables.Disposable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UMB extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(UMB.class.getSimpleName());
    private EventBus bus;
    public Map<String, Connection> busListeners = new HashMap<>();
    public Map<String, Disposable> disposables = new HashMap<>();
    public static final String svcStart = "umb.messages.start";
    public static final String svcStop = "umb.messages.stop";

    public MessageHandler<DefaultResult>
    defaultBusHandler() {
        return (ObjectNode node) -> {
            MessageResult<DefaultResult> result = new MessageResult<>();
            if (node == null) {
                logger.warn("No message was received");
                result.setStatus(MessageResult.Status.NO_MESSAGE);
                return result;
            }

            String body = node.toString();
            result.setStatus(MessageResult.Status.SUCCESS);
            result.setBody(body);
            result.setNode(node);
            return result;
        };
    }

    /**
     * Creates a CIBusListener to listen to a certain topic address, and filtering by a selector
     *
     * The CIBusListener uses the tapIntoMessageBus to get messages from the topic.  These messages are passed into
     * the object's nodeSub field.  This is a Subject\<ObjectNode\>, which is also an Observable.  Every time a new
     * message from the UMB comes in, it is passed to the CIBusListener's nodeSub.
     *
     * By subscribing to the nodeSub, we can listen for each item emitted by the Subject.  Each item in turn will get
     * passed to the websocket so that as messages come in, the client will receive them over the socket.
     *
     * @param req
     * @return
     */
    public Tuple<Optional<Connection>, Optional<Disposable>>
    makeUMBListener(UMBListenerData req) {
        String address = req.getTopic();
        String selector = req.getSelector();
        String dest = req.getBusAddress();
        String remote = req.clientAddress;

        Tuple<Optional<Connection>, Optional<Disposable>> maybe =
                new Tuple<>(Optional.empty(), Optional.empty());
        JsonObject jo = new JsonObject();
        MessageHandler<DefaultResult> hdlr = this.defaultBusHandler();
        String brokerCfgPath = BrokerConfig.getDefaultConfigPath();
        try {
            BrokerConfig brokerCfg = Serializer.fromYaml(BrokerConfig.class, new File(brokerCfgPath));
            CIBusListener<DefaultResult> cbl = new CIBusListener<>(hdlr, brokerCfg);
            Optional<Connection> isConn = cbl.tapIntoMessageBus(selector, cbl.createListener(cbl.messageParser()), address);

            String clientId = String.format("%s-%s", req.getTag(), remote);
            jo.put("id", clientId);
            if (isConn.isPresent())
                jo.put("message", String.format("Listening to %s", address));
            else
                jo.put("message", String.format("Could not subscribe to %s", address));

            Disposable disp = cbl.getNodeSub().subscribe(
                    next -> {
                        JsonObject ijo = new JsonObject();
                        ijo.put("id", clientId);
                        ijo.put("message", next.toString());
                        this.bus.publish(dest, ijo.encode());
                    },
                    err -> {
                        String error = "Error getting messages from UMB";
                        logger.error(error);
                        JsonObject ejo = new JsonObject();
                        ejo.put("id", clientId);
                        ejo.put("message", error);
                        this.bus.publish(dest, ejo.encode());
                    });
            maybe.first = isConn;
            maybe.second = Optional.of(disp);
        } catch (IOException e) {
            e.printStackTrace();
            jo.put("id", "none");
            jo.put("message", String.format("Could not get broker configuration from %s", brokerCfgPath));
            this.bus.publish(dest, jo.encode());
        }

        this.bus.publish(dest,jo.encode());
        return maybe;
    }

    public void start() {
        this.bus = vertx.eventBus();

        // TODO: Need a way unregister any handlers
        // The handler that will start a listener
        this.bus.consumer(UMB.svcStart, (Message<String> msg) -> {
            String body = msg.body();
            try {
                UMBListenerData data = Serializer.from(UMBListenerData.class, body);
                Tuple<Optional<Connection>, Optional<Disposable>> ret =
                        this.makeUMBListener(data);

                // TODO: close the connection
                String clientID = String.format("%s-%s", data.getTag(), data.clientAddress);
                ret.first.ifPresent(conn -> {
                    this.busListeners.put(clientID, conn);
                });
                ret.second.ifPresent(disp -> {
                    this.disposables.put(clientID, disp);
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        this.bus.consumer(UMB.svcStop, (Message<String> msg) -> {
            String body = msg.body();
            try {
                UMBListenerData data = Serializer.from(UMBListenerData.class, body);
                String clientID = String.format("%s-%s", data.getTag(), data.clientAddress);

                Disposable disp = this.disposables.get(clientID);
                if (disp != null)
                    disp.dispose();
                Connection conn= this.busListeners.get(clientID);
                if (conn != null)
                    conn.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JMSException e) {
                e.printStackTrace();
                logger.error("JMS Exception closing connection");
            }
        });
    }
}
