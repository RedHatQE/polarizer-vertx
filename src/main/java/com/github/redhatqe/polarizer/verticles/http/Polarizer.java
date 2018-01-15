package com.github.redhatqe.polarizer.verticles.http;


import com.github.redhatqe.polarizer.importer.XUnitService;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.reporter.XUnitReporter;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.reporter.utils.Tuple;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.verticles.http.data.IComplete;
import com.github.redhatqe.polarizer.verticles.http.data.TestCaseData;
import com.github.redhatqe.polarizer.verticles.http.data.XUnitData;
import com.github.redhatqe.polarizer.verticles.http.data.XUnitGenData;
import com.github.redhatqe.polarizer.verticles.tests.APITestSuite;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.functions.Consumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.WorkerExecutor;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerFileUpload;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;


public class Polarizer extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "port";
    public static final String CONFIG_HTTP_SERVER_HOST = "host";
    public static final String UPLOAD_DIR = "/tmp";
    private EventBus bus;
    private int port;

    /**
     * This method will take the bytes from the upload handler and accumulate them to a Buffer.  Once the completion
     * event (from the Flowable object created from the upload object) is sent, serialize an object.  That means that
     * the data that was sent over the wire must be serializable into a class object based on Serializer.from method.
     * Once the data object has been deserialized into an Object type, call the supplied Consumer function on it to
     * perform some side effect (as all Consumer functions do).
     *
     * The purpose of this function is to:
     * - Buffer up and accumulate data chunks coming over the wire
     * - Deserialize buffered data to an Object
     * - Call a side-effecting function on the Object
     * - Mutate the state of the data object
     * - Pass the data object to the emitter's onNext (if successful) or call emitter's onError if not
     *
     * @param upload upload handler object
     * @param t Tuple containing the String and UUID
     * @param data The data (of type T) to pass through to emitter's onNext
     * @param cls An class type to serialize from
     * @param fn Consumer function that takes a U type and calls it (recall Consumers return nothing)
     * @param emitter an ObservableEmitter to pass data/error/completions to
     * @param <T> The type of the data that will be passed to emitter
     * @param <U> The type of the argument the Consumer expects
     */
    private <T extends IComplete, U> void
    argsUploader( HttpServerFileUpload upload
                , Tuple<String, UUID> t
                , T data
                , Class<U> cls
                , Consumer<U> fn
                , ObservableEmitter<T> emitter) {
        // Instead of streaming to the filesystem then deserialize, just deserialize from buffer
        // As the file upload chunks come in, the next handler will append them to buff.  Once we have a
        // completion event, we can convert the buffer to a string, and deserialize into our object
        Buffer buff = Buffer.buffer();
        upload.toFlowable().subscribe(
                buff::appendBuffer,
                err -> logger.error(String.format("Could not upload %s file for %s", t.first, t.second.toString())),
                () -> {
                    logger.info(String.format("%s file for %s has been fully uploaded", t.first, t.second.toString()));
                    U xargs;
                    try {
                        xargs = Serializer.from(cls, buff.toString());
                        fn.accept(xargs);
                        data.addToComplete(t.first);
                        emitter.onNext(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                        emitter.onError(e);
                    }
                });
    }

    /**
     * This method is similar to the argsUploader method, but instead of deserializing the buffered data into an Object,
     * this writes the data to the file system.
     *
     * @param upload
     * @param t
     * @param path
     * @param data
     * @param emitter
     * @param fn
     * @param <T>
     */
    private <T extends IComplete> void
    fileUploader( HttpServerFileUpload upload
                , Tuple<String, UUID> t
                , Path path
                , T data
                , ObservableEmitter<T> emitter
                , Consumer<String> fn) {
        Buffer buff = Buffer.buffer();
        logger.debug("upload object: "  + upload.toString());
        upload.toFlowable().subscribe(
                buff::appendBuffer,
                err -> logger.error(String.format("Could not upload %s file", t.first)),
                () -> {
                    logger.info(String.format("%s file for %s has been fully uploaded", t.first, t.second));
                    try {
                        FileHelper.writeFile(path, buff.toString());
                        fn.accept(path.toString());
                        data.addToComplete(t.first);
                        emitter.onNext(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                        emitter.onError(e);
                    }
                });
    }

    /**
     * Creates an Observable that works for multipart uploads (eg when you use curl -F).  Each upload request will be
     * tagged with a name, which will be one of xunit|xargs|mapping.  Depending on which upload tag is received, the
     * method will do the appropriate thing.
     *
     * Note that the calls to onNext/onError are handled in the argsUploader and fileUploader methods.
     *
     * @param id
     * @param req
     * @return
     */
    private Observable<XUnitGenData> makeXGDObservable(UUID id, HttpServerRequest req) {
        return Observable.create(emitter -> {
            try {
                req.uploadHandler(upload -> {
                    String fname = upload.name();
                    Path path;
                    XUnitGenData data;
                    Tuple<String, UUID> t;
                    switch (fname) {
                        case "xunit":
                            path = FileHelper.makeTempPath("/tmp", "polarion-result-", ".xml", null);
                            t = new Tuple<>("xunit", id);
                            data = new XUnitGenData(id);
                            this.fileUploader(upload, t, path, data, emitter, data::setXunitPath);
                            break;
                        case "xargs":
                            data = new XUnitGenData(id);
                            t = new Tuple<>("xargs", id);
                            this.argsUploader(upload, t, data, XUnitConfig.class, data::setConfig, emitter);
                            break;
                        case "mapping":
                            path = FileHelper.makeTempPath("/tmp", "mapping-", ".json", null);
                            t = new Tuple<>("mapping", id);
                            data = new XUnitGenData(id);
                            this.fileUploader(upload, t, path, data, emitter, data::setMapping);
                            break;
                        default:
                            break;
                    }
                });
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    /**
     * Handler for the /xunit/generate endpoint
     *
     * This method takes a non-polarion compliant xunit file, a polarizer-xunit.json config file, and the mapping.json
     * file, and returns a compliant xunit file ready to be sent to polarion.
     *
     * @param rc
     */
    private void xunitGenerator(RoutingContext rc) {
        logger.info("In xunitGenerator");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        Observable<XUnitGenData> s$ = this.makeXGDObservable(id, req);
        // Scan is like a continuing reduce that accumulates a partial result on each new item rather than wait for all
        // items in the Observable to finish sending (which if the Observable never sends a completion event, will never
        // happen).
        s$.scan(XUnitGenData::merge)
                .subscribe(xgd -> {
                    if (xgd.done()) {
                        XUnitConfig config = xgd.getConfig();
                        config.setCurrentXUnit(xgd.getXunitPath());
                        config.setMapping(xgd.getMapping());
                        XUnitReporter.createPolarionXunit(config);

                        JsonObject jo = new JsonObject();
                        try {
                            // TODO: This could be a huge file, so we should use an async read file
                            jo.put("newxunit", FileHelper.readFile(config.getNewXunit()));
                            jo.put("status", "passed");
                            req.response().end(jo.encode());
                        } catch (IOException e) {
                            e.printStackTrace();
                            jo.put("status", "failed");
                            req.response().end(jo.encode());
                        }
                    }
                    else {
                        logger.info("Not all parameters uploaded yet");
                        logger.info(xgd.getCompleted());
                    }
                }, err -> {
                    logger.error("Failure getting uploaded data " + err.getMessage());
                    JsonObject jo = new JsonObject();
                    jo.put("status", "error");
                    req.response().end(jo.encode());
                }, () -> {

                });
    }

    private Observable<XUnitData> makeXImpObservable(UUID id, HttpServerRequest req) {
        return Observable.create(emitter -> {
            try {
                req.uploadHandler(upload -> {
                    String fname = upload.name();
                    XUnitData data;
                    Tuple<String, UUID> t;
                    switch (fname) {
                        case "xunit":
                            Path path = FileHelper.makeTempPath("/tmp", "polarion-result-", ".xml", null);
                            t = new Tuple<>("xunit", id);
                            data = new XUnitData(id);
                            this.fileUploader(upload, t, path, data, emitter, data::setXunitPath);
                            break;
                        case "xargs":
                            data = new XUnitGenData(id);
                            t = new Tuple<>("xargs", id);
                            this.argsUploader(upload, t, data, XUnitConfig.class, data::setConfig, emitter);
                            break;
                        default:
                            break;
                    }
                });
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    /**
     * Handler for the /xunit/import endpoint.
     *
     * Takes a XUnit Importer compliant xunit xml file and a polarizer-xunit.json config, and resends it to the polarion
     * server.  Currently, this method will block until a response message is received from the UMB for the response or
     * until the timeout passes (configured in the broker.config file).
     *
     * FIXME: Probably not a good idea to block since that will hold open a socket connection or require some kind of
     * session timeout heartbeat.  Ideally, this should be "fire and forget".  To know if a response got completed,
     * the message can go to a persistent queue and check for the response message there.  If there is no response after
     * the default timeout, a user can check the Polarion browser queue to see if it's still in the queue, and if the
     * request is still in the queue, keep waiting.  If it is not in the queue, and the response timed out, then some
     * kind of error happened, and user can retry.
     *
     * @param rc context passed by server
     */
    private void xunitImport(RoutingContext rc) {
        logger.info("In xunitImport");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        Observable<XUnitData> s$ = this.makeXImpObservable(id, req);
        // Once we have a "complete" XUnitData object, make a XUnitService request.  Once that is complete, send a
        // response back.  The XUnitService.request() is performed in a worker verticle since it blocks
        // TODO: Make this a websocket since it can take a long time for XUnitService.request to complete.  Ideally,
        // this should be "fire and forget".  If we need to know if it got completed, we should have a persistent
        // queue "mailbox" to be notified if the request went through.
        s$.scan(XUnitData::merge)
                .subscribe((XUnitData xu) -> {
                    if (xu.done()) {
                        // Run this code in a Worker Verticle, since this can take a long time.
                        WorkerExecutor executor = vertx.createSharedWorkerExecutor("XUnitService.request");
                        executor.rxExecuteBlocking((Future<JsonObject> fut) -> {
                            try {
                                JsonObject jo = XUnitService.request(xu.getConfig());
                                fut.complete(jo);
                            } catch (IOException e) {
                                e.printStackTrace();
                                fut.fail(e);
                            }
                        }).subscribe(item -> {
                            req.response().end(item.encode());
                        });
                    }
                }, err -> {
                    JsonObject jo = new JsonObject();
                    String msg = "Error with upload";
                    logger.error(msg);
                    jo.put("status", "failed");
                    req.response().end(jo.encode());
                });
    }

    // FIXME: This seems to hang while uploading or reading in the jar file
    private Observable<TestCaseData> makeTCMapperObservable(UUID id, HttpServerRequest req) {
        return Observable.create(emitter -> {
            try {
                req.uploadHandler(upload -> {
                    String fname = upload.name();
                    TestCaseData data = new TestCaseData(id);
                    Path path;
                    Tuple<String, UUID> t;
                    switch (fname) {
                        case "jar":
                            path = FileHelper.makeTempPath("/tmp", "jar-to-check-", ".jar", null);
                            t = new Tuple<>("jar", id);
                            this.fileUploader(upload, t, path, data, emitter, data::setJarToCheck);
                            break;
                        case "mapping":
                            path = FileHelper.makeTempPath("/tmp","/tmp/mapping-", ".json", null);
                            t = new Tuple<>("mapping", id);
                            this.fileUploader(upload, t, path, data, emitter, data::setMapping);
                            break;
                        case "xargs":
                            t = new Tuple<>("tcargs", id);
                            this.argsUploader(upload, t, data, TestCaseConfig.class, data::setConfig, emitter);
                            break;
                        default:
                            break;
                    }
                });
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    /**
     *
     * @param rc context passed by server
     */
    private void testCaseMapper(RoutingContext rc) {
        logger.info("In testcaseMapper");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();
        Observable<TestCaseData> s$ = this.makeTCMapperObservable(id, req);
        s$.scan(TestCaseData::merge)
                .subscribe(data -> {
                    if (data.done()) {
                        JsonObject jo;
                        TestCaseConfig cfg = data.getConfig();
                        try {
                            jo = MainReflector.process(cfg);
                            jo.put("result", "passed");
                        } catch (IOException ex) {
                            jo = new JsonObject();
                            jo.put("result", "failed");
                        }
                        req.response().end(jo.encode());
                    }
                }, err -> {
                    logger.error("Failed uploading necessary files");
                    JsonObject jo = new JsonObject();
                    jo.put("result", "error");
                    jo.put("message", "Failed uploading necessary files");
                });
    }


    /**
     *
     * @param rc context passed by server
     */
    private void testcaseImport(RoutingContext rc) {
        logger.info("In testcaseImport");
        HttpServerRequest req = rc.request();

        UUID id = UUID.randomUUID();

    }

    /**
     * Makes a request to the APITestSuite verticle to run tests
     *
     * TODO:  Need to be able to differentiate suites to run.
     * TODO:  Need to be able to see the test results live (perhaps make this a websocket)
     *
     * @param ctx RoutingContext supplied by vertx
     */
    private void test(RoutingContext ctx) {
        HttpServerRequest req = ctx.request();
        req.bodyHandler(upload -> {
            logger.info("Got the test config file");
            String body = upload.toString();
            // Send message on event bus to the APITestSuite Verticle
            String address = APITestSuite.class.getCanonicalName();
            // FIXME: Tried using rxSend() but got an error that no consumer was registered
            this.bus.send(address, body);
            JsonObject jo = new JsonObject();
            jo.put("result", "Kicking off tests");
            req.response().end(jo.encode());
        });
    }

    private void hello(RoutingContext rc) {
        HttpServerRequest req = rc.request();
        JsonObject jo = new JsonObject();
        jo.put("result", "congratulations, server responded");
        req.response().end(jo.encode());
    }

    public HttpServerOptions setupTLS(HttpServerOptions opts) {
        String keystore = this.config().getString("keystore-path");
        String keypass = this.config().getString("keystore-password");
        if (opts == null)
            opts = new HttpServerOptions();
        return opts
                .setSsl(true)
                .setKeyStoreOptions(new JksOptions()
                        .setPath(keystore)
                        .setPassword(keypass));
    }

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
            router.route("/testcase/mapper").method(HttpMethod.POST).handler(this::testCaseMapper);
            router.post("/xunit/generate").handler(this::xunitGenerator);
            router.post("/xunit/import").handler(this::xunitImport);
            router.post("/testcase/import").handler(this::testcaseImport);
            router.post("/test").handler(this::test);
            router.get("/hello").handler(this::hello);

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
