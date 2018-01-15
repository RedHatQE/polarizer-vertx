package com.github.redhatqe.polarizer.verticles.messaging;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UMB extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(UMB.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "port";
    public static final String CONFIG_HTTP_SERVER_HOST = "host";
    public static final String UPLOAD_DIR = "/tmp";
    private EventBus bus;
    private int port;

    public void start() {
        this.bus = vertx.eventBus();
        port = config().getInteger(CONFIG_HTTP_SERVER_PORT, 9000);
        String host = config().getString(CONFIG_HTTP_SERVER_HOST, "rhsm-cimetrics.usersys.redhat.com");
        HttpServerOptions opts = new HttpServerOptions()
                .setHost(host)
                .setReusePort(true);
        HttpServer server = vertx.createHttpServer(opts);  // TODO: pass opts to the method for TLS
        Router router = Router.router(vertx);

        server.requestHandler(req -> {
            req.setExpectMultipart(true);

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
