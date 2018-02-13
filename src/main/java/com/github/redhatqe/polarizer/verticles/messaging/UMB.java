package com.github.redhatqe.polarizer.verticles.messaging;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.data.ProcessingInfo;
import com.github.redhatqe.polarizer.messagebus.CIBusListener;
import com.github.redhatqe.polarizer.messagebus.DefaultResult;
import com.github.redhatqe.polarizer.messagebus.MessageHandler;
import com.github.redhatqe.polarizer.messagebus.MessageResult;
import com.github.redhatqe.polarizer.messagebus.config.BrokerConfig;
import com.github.redhatqe.polarizer.messagebus.utils.Tuple;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import io.reactivex.Observable;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.core.http.ServerWebSocket;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.Connection;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UMB extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(UMB.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "port";
    public static final String CONFIG_HTTP_SERVER_HOST = "host";
    public static final String UPLOAD_DIR = "/tmp";
    private static Integer clientID = 0;
    private EventBus bus;
    private int port;
    public Map<Integer, Tuple<CIBusListener<ProcessingInfo>, Connection>> buses = new HashMap<>();
    public Map<Integer, ServerWebSocket> sockets = new HashMap<>();

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
    public Observable<JsonObject>
    makeUMBListener(HttpServerRequest req, ServerWebSocket ws) {
        return Observable.create(emitter -> {
            String address = req.getParam("topic");
            String selector = req.getParam("selector");

            JsonObject jo = new JsonObject();
            MessageHandler<DefaultResult> hdlr = this.defaultBusHandler();
            String brokerCfgPath = BrokerConfig.getDefaultConfigPath();
            BrokerConfig brokerCfg = null;
            try {
                brokerCfg = Serializer.fromYaml(BrokerConfig.class, new File(brokerCfgPath));
                CIBusListener<ProcessingInfo> cbl = new CIBusListener<>(hdlr, brokerCfg);
                Optional<Connection> isConn = cbl.tapIntoMessageBus(selector, cbl.createListener(cbl.messageParser()), address);

                if (isConn.isPresent()) {
                    Connection conn = isConn.get();
                    Tuple<CIBusListener<ProcessingInfo>, Connection> t = new Tuple<>(cbl, conn);
                    if (clientID < 0)
                        clientID = 0;
                    clientID++;
                    this.buses.put(clientID, t);
                    this.sockets.put(clientID, ws);
                    jo.put("id", clientID.toString());
                    jo.put("message", String.format("Listening to %s", address));
                }
                else {
                    jo.put("id", "none");
                    jo.put("message", String.format("Could not subscribe to %s", address));
                    ws.close();
                }

                cbl.getNodeSub().subscribe(
                        next -> {
                            JsonObject ijo = new JsonObject();
                            ijo.put("message", next.toString());
                            //ws.writeFinalTextFrame(jo.encode());
                            emitter.onNext(ijo);
                        },
                        err -> {
                            String error = "Error getting messages from UMB";
                            logger.error(error);
                            JsonObject ejo = new JsonObject();
                            ejo.put("message", error);
                            //ws.writeFinalTextFrame(ejo.encode());
                            emitter.onNext(ejo);
                        });
            } catch (IOException e) {
                e.printStackTrace();
                jo.put("id", "none");
                jo.put("message", String.format("Could not get broker configuration from %s", brokerCfgPath));
                emitter.onError(e);
                ws.close();
            }
            //ws.writeFinalTextFrame(jo.encode());
            emitter.onNext(jo);
        });
    }

    public void listen(RoutingContext rc) {
        HttpServerRequest req = rc.request();
        // TODO: I think this has to be long lived.  We also need a way to close
        ServerWebSocket ws = req.upgrade();

        Observable<JsonObject> m$ = this.makeUMBListener(req, ws);
        m$.subscribe((JsonObject next) -> ws.writeFinalTextFrame(next.encode()),
                err -> {
                    logger.error("Could not get message");
                    ws.close();
                });
    }

    public void start() {
        this.bus = vertx.eventBus();
        port = config().getInteger(CONFIG_HTTP_SERVER_PORT, 9001);
        String host = config().getString(CONFIG_HTTP_SERVER_HOST, "rhsm-cimetrics.usersys.redhat.com");
        HttpServerOptions opts = new HttpServerOptions()
                .setMaxWebsocketFrameSize(1024 * 1024)     // 1Mb max
                .setHost(host)
                .setReusePort(true);
        HttpServer server = vertx.createHttpServer(opts);  // TODO: pass opts to the method for TLS
        Router router = Router.router(vertx);

        server.requestHandler(req -> {
            req.setExpectMultipart(true);

            router.post("/umb/:topic/:selector").handler(this::listen);
            router.route().handler(BodyHandler.create()
                    .setBodyLimit(209715200L)                 // Max Jar size is 200MB
                    .setDeleteUploadedFilesOnEnd(false)       // FIXME: for testing only.  In Prod set to true
                    .setUploadsDirectory(UPLOAD_DIR));

            router.accept(req);
        })
        .rxListen(port, host)
        .subscribe(succ -> logger.info(String.format("Server is now listening on %s:%d", host, this.port)),
                   err -> logger.info(String.format("Server could not be started %s", err.getMessage())));
    }
}
