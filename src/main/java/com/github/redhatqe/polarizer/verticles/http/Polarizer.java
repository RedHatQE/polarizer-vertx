package com.github.redhatqe.polarizer.verticles.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.ImporterRequest;
import com.github.redhatqe.polarizer.importer.XUnitService;
import com.github.redhatqe.polarizer.messagebus.*;
import com.github.redhatqe.polarizer.messagebus.config.Broker;
import com.github.redhatqe.polarizer.messagebus.config.BrokerConfig;
import com.github.redhatqe.polarizer.reflector.MainReflector;
import com.github.redhatqe.polarizer.reporter.IdParams;
import com.github.redhatqe.polarizer.reporter.XUnitReporter;
import com.github.redhatqe.polarizer.reporter.configuration.Selector;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.TestCaseInfo;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.reporter.importer.testcase.Parameter;
import com.github.redhatqe.polarizer.reporter.importer.testcase.Testcases;
import com.github.redhatqe.polarizer.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarizer.reporter.jaxb.JAXBReporter;
import com.github.redhatqe.polarizer.reporter.utils.Tuple;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.Tuple3;
import com.github.redhatqe.polarizer.verticles.messaging.UMB;
import com.github.redhatqe.polarizer.verticles.proto.TestCaseMessage;
import com.github.redhatqe.polarizer.verticles.proto.TextMessage;
import com.github.redhatqe.polarizer.verticles.proto.UMBListenerData;
import com.github.redhatqe.polarizer.verticles.http.data.*;
import com.github.redhatqe.polarizer.verticles.proto.XUnitImportMessage;
import com.github.redhatqe.polarizer.verticles.tests.APITestSuite;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
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
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.http.*;
import io.vertx.reactivex.core.net.SocketAddress;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.Connection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class Polarizer extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String CONFIG_HTTP_SERVER_PORT = "port";
    public static final String TCIMPORT_BUS = "testcase.import";
    public static final String TCIMPORT_TAG_PREFIX = "testcase-import";
    public static final String XUNITIMPORT_BUS = "xunit.import";
    public static final String XUNITIMPORT_TAG_PREFIX = "xunit-import";
    public static final String UPLOAD_DIR = "/tmp";
    public static final String BUS_TESTCASE_HDLR = "testcase.listener";
    private EventBus bus;
    private int port;
    public Map<String, ServerWebSocket> sockets = new HashMap<>();

    public void start() {
        //Polarizer.test_();
        this.bus = vertx.eventBus();
        port = config().getInteger(CONFIG_HTTP_SERVER_PORT, 9000);
        logger.info(String.format("Starting polarizer web service on port %d", port));
        //String host = config().getString(CONFIG_HTTP_SERVER_HOST, "rhsm-cimetrics.usersys.redhat.com");
        HttpServerOptions opts = new HttpServerOptions()
                .setMaxWebsocketFrameSize(1024 * 1024)     // 1Mb max
                .setReusePort(true);
        HttpServer server = vertx.createHttpServer(opts);  // TODO: pass opts to the method for TLS
        Router router = Router.router(vertx);

        server.requestHandler(req -> {
            req.setExpectMultipart(true);

            router.route("/testcase/mapper").method(HttpMethod.POST).handler(this::testCaseMapper);
            router.post("/testcase/import").handler(this::oldTestCaseImport);
            router.post("/xunit/generate").handler(this::xunitGenerator);
            router.post("/xunit/import").handler(this::xunitImport);
            router.post("/test").handler(this::test);
            router.post("/queue/:importtype/:queuetype/:jobid").handler(this::queueBrowser);
            router.get("/hello").handler(this::hello);

            router.route().handler(BodyHandler.create()
                    .setBodyLimit(209715200L)                 // Max Jar size is 200MB
                    .setDeleteUploadedFilesOnEnd(false)       // FIXME: for testing only.  In Prod set to true
                    .setUploadsDirectory(UPLOAD_DIR));

            router.accept(req);

            // NOTE: It seems like you can't do the websocket handlers from within the router.  If you do, you will
            // get a failure in the handler when you try to do a request.upgrade().  So, handle them here where the
            // upgrade request seems to work.
            if (req.path().endsWith("/umb/start")) {
                ServerWebSocket ws = req.upgrade();
                this.umbListener(ws);
            }

            if (req.path().contains("/ws/testcase/import")) {
                ServerWebSocket ws = req.upgrade();
                this.testcaseImport(ws);
            }

            if (req.path().contains("/ws/xunit/import")) {
                ServerWebSocket ws = req.upgrade();
                this.xunitImportWS(ws);
            }
        })
        .rxListen(port)
        .subscribe(succ -> logger.info(String.format("Server is now listening on port %d", this.port)),
                err -> {
                    logger.info(String.format("Could not start Server on %d: %s", this.port, err.getMessage()));
                    err.printStackTrace();
                });
    }

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
     * @param upload upload handler object
     * @param t a Tuple containing the type string and UUID that is being worked on
     * @param path path for the file being uploaded
     * @param data The data (of type T) to pass through to emitter's onNext
     * @param emitter an ObservableEmitter to pass data/error/completions to
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
     * @param id UUID to keep track of which request is being handled
     * @param req request from vertx server
     * @return Observable of XUnitGenData
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
     * @param rc RoutingContext from vertx
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

    /**
     * Creates an Observable for xunit imports
     *
     * @param id UUID to identify this item
     * @param req HttpServerRequest from vertx
     * @return Observable stream of XUnitData
     */
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
                        logger.info("Creating WorkerExecutor to run XUnitService.request");
                        // Run this code in a Worker Verticle, since this can take a long time.
                        WorkerExecutor executor = vertx.createSharedWorkerExecutor("XUnitService.request");
                        executor.rxExecuteBlocking((Future<JsonObject> fut) -> {
                            try {
                                logger.info("Calling XUnitService.request");
                                JsonObject jo = XUnitService.request(xu.getConfig());
                                fut.complete(jo);
                            } catch (IOException e) {
                                e.printStackTrace();
                                fut.fail(e);
                            }
                        }).subscribe(item -> {
                            req.response().end(item.encode());
                        }, err -> {
                            logger.error(err.getMessage());
                        });
                    }
                }, err -> {
                    JsonObject jo = new JsonObject();
                    String msg = "Error with upload";
                    logger.error(msg);
                    jo.put("status", "failed");
                    jo.put("errors", msg);
                    req.response().end(jo.encode());
                });
    }

    // FIXME: This seems to hang while uploading or reading in the jar file
    private Observable<TestCaseData>
    makeTCMapperObservable(UUID id, HttpServerRequest req) {
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
                            path = FileHelper.makeTempPath("/tmp","mapping-", ".json", null);
                            t = new Tuple<>("mapping", id);
                            this.fileUploader(upload, t, path, data, emitter, data::setMapping);
                            break;
                        case "tcargs":
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
     * This method will read in the jar file and the mapping file, and determine if a testcase import is needed.
     *
     * Will return a new mapping.json file
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

    private static void
    searchTestCases( String projID
                   , Testcases tcs
                   , String name
                   , String id
                   , Map<String, Map<String, IdParams>> mapping) {
        Map<String, IdParams> inner = mapping.getOrDefault(name, null);
        tcs.getTestcase().forEach(tc -> {
            // If we've already got this in the map, check if the ID is the same.  If not, we've got duplicates
            // which could be a problem
            if (inner != null && inner.containsKey(projID) && !inner.get(projID).id.equals(id)) {
                String err = "%s with id of %s already exists.  However new ID of %s was created";
                logger.error(String.format(err, name, inner.get(name).getId(), id));
                return;
            }
            tc.getTestSteps().getTestStep().stream()
                    .flatMap(ts -> ts.getTestStepColumn().stream())
                    .map(tsc -> {
                        List<Parameter> ps = tsc.getParameter();
                        Map<String, IdParams> mparams;
                        if (!mapping.containsKey(name))
                            mparams = new HashMap<>();
                        else
                            mparams = mapping.get(name);

                        IdParams idp = mparams.getOrDefault(projID, null);
                        if (idp == null)
                            idp = new IdParams();

                        List<String> pnames = ps.stream()
                            .map(param -> {
                                String pname = param.getName();
                                logger.info(String.format("Adding parameter %s to %s", pname, name));
                                return pname;
                            })
                            .collect(Collectors.toList());
                        idp.setParameters(pnames);
                        idp.setId(id);
                        mparams.put(projID, idp);
                        return mparams;
                    })
                    .forEach(mp -> mapping.put(name, mp));
        });
    }

    private static MessageHandler<DefaultResult>
    testcaseImportHandler( String projID
                         , String tcXMLPath
                         , Map<String, Map<String, IdParams>> mapFile
                         , TestCaseInfo tt) {
        return (ObjectNode node) -> {
            MessageResult<DefaultResult> result = new MessageResult<>();
            result.setBody(node.toString());
            if (node.toString().equals("")) {
                logger.warn("No message was received");
                result.setStatus(MessageResult.Status.NO_MESSAGE);
                return result;
            }

            JsonNode root = node.get("root");
            if (root.has("status") && root.get("status").textValue().equals("failed")) {
                result.setStatus(MessageResult.Status.FAILED);
                result.setErrorDetails("status was failed");
                return result;
            }

            JAXBReporter rep = new JAXBReporter();
            Optional<Testcases> isTcs = IJAXBHelper.unmarshaller(Testcases.class, new File(tcXMLPath),
                    rep.getXSDFromResource(Testcases.class));
            if (!isTcs.isPresent()) {
                result.setStatus(MessageResult.Status.ERROR);
                result.setErrorDetails("Could not unmarshall testcases xml file");
                return result;
            }
            Testcases tcs = isTcs.get();
            JsonNode testcases = root.get("import-testcases");
            logger.info(testcases.asText());
            String pf = tt.getPrefix();
            String sf = tt.getSuffix();
            testcases.forEach(n -> {
                // Take off the prefix and suffix from the testcase
                String name = n.get("name").textValue();
                name = name.replace(pf, "").replace(sf, "");

                if (!n.get("status").textValue().equals("failed")) {
                    String id = n.get("id").toString();
                    if (id.startsWith("\""))
                        id = id.substring(1);
                    if (id.endsWith("\""))
                        id = id.substring(0, id.length() -1);
                    logger.info(String.format("Testcase id for %s from message response = %s", name, id));
                    searchTestCases(projID, tcs, name, id, mapFile);
                }
                else {
                    logger.error(String.format("Unable to add %s to mapping file", name));
                }
            });

            result.setStatus(MessageResult.Status.SUCCESS);
            return result;
        };
    }


    /**
     * This function is used with regular http(s) /testcase/import.  For websockets, it will use another method
     *
     * @param id UUID to identify this emitted item
     * @param req HttpServerRequest from vertx
     * @return Observable stream of TestCaseImpData
     */
    private Observable<TestCaseImpData>
    makeTCImpObservable(UUID id, HttpServerRequest req) {
        return Observable.create(emitter -> {
            try {
                req.uploadHandler(upload -> {
                    String fname = upload.name();
                    TestCaseImpData data = new TestCaseImpData(id);
                    Tuple<String, UUID> t;
                    switch (fname) {
                        case "testcase":
                            Path path = FileHelper.makeTempPath("/tmp", "polarion-tc-", ".xml", null);
                            t = new Tuple<>("testcase", id);
                            this.fileUploader(upload, t, path, data, emitter, data::setTestcasePath);
                            break;
                        case "tcargs":
                            t = new Tuple<>("tcargs", id);
                            this.argsUploader(upload, t, data, TestCaseConfig.class, data::setConfig, emitter);
                            break;
                        case "mapping":
                            Path mpath = FileHelper.makeTempPath("/tmp", "polarion-tcmap-", ".json", null);
                            t = new Tuple<>("mapping", id);
                            this.fileUploader(upload, t, mpath, data, emitter, data::setMapping);
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


    private void oldTestCaseImport(RoutingContext ctx) {
        UUID id = UUID.randomUUID();
        HttpServerRequest req = ctx.request();

        Observable<TestCaseImpData> s$ = this.makeTCImpObservable(id, req);
        s$.scan(TestCaseImpData::merge)
            .subscribe(data -> {
                Boolean d = data.done();
                logger.info(String.format("Done is %b", d));
                if (d) {
                    JsonObject jo = new JsonObject();
                    TestCaseConfig cfg = data.getConfig();
                    File mappath = new File(cfg.getMapping());
                    Map<String, Map<String, IdParams>> mapping = FileHelper.loadMapping(mappath);

                    MessageHandler<DefaultResult> hdlr = testcaseImportHandler(cfg.getProject(),
                            data.getTestcasePath(), mapping, cfg.getTestcase());
                    String brokerCfgPath = BrokerConfig.getDefaultConfigPath();
                    BrokerConfig brokerCfg = Serializer.fromYaml(BrokerConfig.class, new File(brokerCfgPath));
                    CIBusListener<DefaultResult> cbl = new CIBusListener<>(hdlr, brokerCfg);

                    File testXml = FileHelper.makeTempFile("/tmp", "testcase-import", ".xml", null);
                    String selName = cfg.getTestcase().getSelector().getName();
                    String selVal = cfg.getTestcase().getSelector().getValue();
                    String selector = String.format( "%s='%s'", selName, selVal);
                    String url = cfg.getServers().get("polarion").getUrl() + cfg.getTestcase().getEndpoint();
                    String address = String.format("Consumer.%s.%s", cbl.getClientID(), CIBusListener.TOPIC);

                    Optional<MessageResult<DefaultResult>> on;
                    logger.info("Sending testcase import request");
                    String user = cfg.getServers().get("polarion").getUser();
                    String domain = cfg.getServers().get("polarion").getDomain();
                    String pw = cfg.getServers().get("polarion").getPassword();
                    on = ImporterRequest.sendImportByTap(cbl, url, domain, user, pw, testXml, selector, address);
                    String msg = getTCImportResult(on);


                    jo.put("result", msg);
                    req.response().end(jo.encode());
                }
                else {
                    String missing = String.join(",", data.done.stream()
                        .filter(e -> !data.completed().contains(e))
                        .collect(Collectors.toList()));
                    logger.info(String.format("Still need [%s] to complete upload", missing));
                }
            }, err -> {
                logger.error("Failed uploading necessary files");
                JsonObject jo = new JsonObject();
                jo.put("result", "error");
                jo.put("message", "Failed uploading necessary files");
            });
    }


    /**
     * This method is used for gathering data from a websocket transmission
     *
     * @param ws ServerWebSocket from vertx
     * @param cls class to serialize data into
     * @param <T> type of class to serialize
     * @return Observable of type T
     */
    private <T extends TextMessage> Observable<T>
    makeWebsocketObservable(ServerWebSocket ws, Class<T> cls) {
        Buffer buffer = Buffer.buffer();
        return Observable.create(emitter -> ws.frameHandler(frame -> {
            logger.info("Received a frame");
            if (frame.isText()) {
                logger.info("Appending text frame to buffer");
                buffer.appendString(frame.textData());
            }
            if (frame.isBinary()) {
                logger.info("Appending binary frame to buffer");
                buffer.appendBuffer(frame.binaryData());
            }
            if (frame.isFinal()) {
                logger.info("All data sent from client");
                T request;
                try {
                    String body = buffer.toString();
                    logger.info(String.format("Data:\n%s", body));
                    request = Serializer.from(cls, buffer.toString());
                    if (request.getAck()) {
                        // TODO: Need to check by Op code if the reply requires a nak
                        JsonObject reply = request.makeReplyMessage("Received message", false);
                        ws.writeFinalTextFrame(reply.encode());
                    }
                    logger.info(String.format("Deserialized buffer into %s object", cls.getCanonicalName()));
                    emitter.onNext(request);
                    emitter.onComplete();
                } catch (IOException e) {
                    e.printStackTrace();
                    emitter.onError(e);
                } finally {
                    buffer.setBuffer(0, Buffer.buffer());
                }
            }
        }));
    }

    class ImportArgs {
        File xml;
        String selectorName;
        String selectorValue;
        String domain;
        String url;
        String user;
        String pw;
        String address;

        ImportArgs(TestCaseConfig cfg, String address) {
            this.selectorName = cfg.getTestcase().getSelector().getName();
            this.selectorValue = cfg.getTestcase().getSelector().getValue();
            this.xml = new File(cfg.getCurrentTCXml());
            this.domain = cfg.getServers().get("polarion").getDomain();
            this.url = cfg.getServers().get("polarion").getUrl() + cfg.getTestcase().getEndpoint();
            this.user = cfg.getServers().get("polarion").getUser();
            this.pw = cfg.getServers().get("polarion").getPassword();
            this.address = address;
        }

        ImportArgs(XUnitConfig cfg, String address) {
            this.selectorName = cfg.getXunit().getSelector().getName();
            this.selectorValue = cfg.getXunit().getSelector().getValue();
            this.xml = new File(cfg.getCurrentXUnit());
            this.domain = cfg.getServers().get("polarion").getDomain();
            this.url = cfg.getServers().get("polarion").getUrl() + cfg.getXunit().getEndpoint();
            this.user = cfg.getServers().get("polarion").getUser();
            this.pw = cfg.getServers().get("polarion").getPassword();
            this.address = address;
        }

        String selector() {
            return String.format( "%s='%s'", selectorName, selectorValue);
        }
    }

    private Single<JsonObject>
    executeTCImport( WorkerExecutor executor
                   , ImportArgs args
                   , CIBusListener<DefaultResult> cbl
                   , String address
                   , JsonObject jo) {
        return executor.rxExecuteBlocking((Future<JsonObject> fut) -> {
            Optional<MessageResult<DefaultResult>> on;
            List<Tuple<String, File>> files = new ArrayList<>();
            files.add(new Tuple<>("file", args.xml));
            on = ImporterRequest.sendImport(cbl, args.domain, args.url, args.user, args.pw, files,
                    args.selector(), address);

            // Get the immediate return from the post.  Don't wait for UMB response
            String msg = this.getTCImportResult(on);
            jo.put("result", msg);
            fut.complete(jo);
        });
    }

    private Single<JsonObject>
    executeImport( WorkerExecutor executor
                 , ImportArgs args
                 , CIBusListener<DefaultResult> cbl
                 , String address
                 , JsonObject jo) {
        return executor.rxExecuteBlocking((Future<JsonObject> fut) -> {
            Optional<MessageResult<DefaultResult>> on;
            List<Tuple<String, File>> files = new ArrayList<>();
            files.add(new Tuple<>("file", args.xml));
            on = ImporterRequest.sendImport(cbl, args.url, args.domain, args.user, args.pw, files,
                    args.selector(), address);

            // Get the immediate return from the post.  Don't wait for UMB response
            String msg = this.getTCImportResult(on);
            jo.put("result", msg);
            fut.complete(jo);
        });
    }

    /**
     * Request a listener
     *
     * @param hdlr handler to parse JMS messaae
     * @param umb data coming from umb
     * @return tuple of optional Connectio and Disposables
     */
    private Tuple3<CIBusListener<DefaultResult>
                  , Optional<Connection>
                  , Optional<Disposable>>
    makeTestCaseListener( MessageHandler<DefaultResult> hdlr
                        , UMBListenerData umb) {
        Tuple3<CIBusListener<DefaultResult>, Optional<Connection>, Optional<Disposable>> maybe = new Tuple3<>();
        maybe.second = Optional.empty();
        maybe.third = Optional.empty();
        JsonObject jo = new JsonObject();

        String brokerCfgPath = BrokerConfig.getDefaultConfigPath();
        try {
            BrokerConfig brokerCfg = Serializer.fromYaml(BrokerConfig.class, new File(brokerCfgPath));
            CIBusListener<DefaultResult> cbl = new CIBusListener<>(hdlr, brokerCfg);
            Optional<Connection> isConn = cbl.tapIntoMessageBus(umb.getSelector(), cbl.createListener(cbl.messageParser()), umb.getTopic());

            String clientId = String.format("%s-%s", umb.getTag(), umb.clientAddress);
            jo.put("id", clientId);
            if (isConn.isPresent())
                jo.put("message", String.format("Listening to %s", umb.getTopic()));
            else
                jo.put("message", String.format("Could not subscribe to %s", umb.getTopic()));

            Broker brk = brokerCfg.getBrokers().get("ci");
            Long timeout = 300000L;
            long msgCount = 1L;
            if (brk != null) {
                timeout = brk.getMessageTimeout();
                msgCount = Integer.toUnsignedLong(brk.getMessageMax());
            }

            // As the MessageHandler gets messages from the Message Bus, it will pass through the nodeSub.  This will
            // call the hdlr.handle() which will transform the ObjectNode into a DefaultResult.  This DefaultResult
            // object is then forwarded to the cbl.resultSubject.  So each time this happens, subscribe
            Disposable disp = cbl.getResultSubject()
                .timeout(timeout, TimeUnit.MILLISECONDS)
                .take(msgCount)
                .subscribe((next) -> {
                    JsonObject ijo = new JsonObject();
                    ijo.put("id", clientId);
                    ijo.put("message", next.getBody());
                    this.bus.publish(umb.getBusAddress(), ijo.encode());
                }, err -> {
                    String error = "Error getting messages from UMB";
                    logger.error(error);
                    JsonObject ejo = new JsonObject();
                    ejo.put("id", clientId);
                    ejo.put("message", error);
                    this.bus.publish(umb.getBusAddress(), ejo.encode());
                });
            maybe.first = cbl;
            maybe.second = isConn;
            maybe.third = Optional.of(disp);
        } catch (IOException e) {
            maybe.first = null;
            e.printStackTrace();
            jo.put("id", "none");
            jo.put("message", String.format("Could not get broker configuration from %s", brokerCfgPath));
            this.bus.publish(umb.getBusAddress(), jo.encode());
        }

        this.bus.publish(umb.getBusAddress(),jo.encode());
        return maybe;
    }

    private static void
    bufferToWebSocket(ServerWebSocket ws, Buffer item, int maxsize) {
        int end = item.length();
        if (item.length() < maxsize) {
            ws.writeFinalBinaryFrame(item);
        }
        else {
            io.vertx.core.buffer.Buffer buff = io.vertx.core.buffer.Buffer.buffer(item.length());
            item.writeToBuffer(buff);
            int start = 0;
            while(buff.length() > maxsize) {
                if (start == end) {
                    ws.writeFinalTextFrame(buff.toString());
                }
                int len = buff.length() - start;
                int transmit = len < maxsize ? len : maxsize;
                byte[] content = new byte[transmit];
                io.vertx.core.buffer.Buffer c = buff.getBytes(start, transmit, content);

                ws.writeBinaryMessage(Buffer.newInstance(c));
                start += transmit;
                buff = buff.getBuffer(start, end);
            }
            // The while loop ended, meaning that buff.length() is less than maxsize.  so we can
            // transmit the final frame if start != end.
            if (start != end) {
                ws.writeFinalBinaryFrame(Buffer.newInstance(buff));
            }
        }
    }

    /**
     * TODO: Figure out how to make this more generic
     *
     * This function is a bridge from the CIBusListener, listening to the JMS broker, to a websocket
     *
     */
    private void
    jmsToWebSocket( ServerWebSocket ws
                  , UMBListenerData umb) {
        // We're going to listen to the messages published to
        // in, let the MessageHandler parse the ObjectNode.
        // TODO: for the timeout operator, if we surpass this, make it call a function to check the queue browser
        // TODO: Figure out a way to limit the number emitted
        logger.info("Start listening for JMS messages to bridge to websocket");

        this.bus.consumer(umb.getBusAddress(), (Message<String> msg) -> {
            // TODO: Might need to use bufferToWebSocket
            ws.writeTextMessage(msg.body());
        });
    }

    public static UMBListenerData
    createUMBListenerData(Selector sel, String tag, String busAddress) {
        String selector = String.format("%s='%s'", sel.getName(), sel.getValue());
        UMBListenerData.UMBAction act = UMBListenerData.UMBAction.START;
        String id = UUID.randomUUID().toString();
        tag = String.format("%s-%s", tag, id);
        busAddress = String.format("%s.%s", busAddress, id);
        UMBListenerData umb = UMBListenerData.makeDefault(act, selector, tag);
        umb.setBusAddress(busAddress);

        return umb;
    }

    private void xunitImportWS(ServerWebSocket ws) {
        logger.info("In xunitImportWS");

        Observable<XUnitImportMessage> s$ = this.makeWebsocketObservable(ws, XUnitImportMessage.class);
        s$.map(XUnitImportMessage::createXUnitData)
            .subscribe(data -> {
                JsonObject jo = new JsonObject();
                XUnitConfig cfg = data.getConfig();

                MessageHandler<DefaultResult> hdlr = XUnitService.xunitMsgHandler();
                Selector sel = cfg.getXunit().getSelector();
                UMBListenerData umb = createUMBListenerData(sel, XUNITIMPORT_TAG_PREFIX, XUNITIMPORT_BUS);

                // Bridge the JMS to the websocket
                jmsToWebSocket(ws, umb);

                // Create the CIBusListener, the Connection (so we can close) and a way to cancel
                Tuple3<CIBusListener<DefaultResult>, Optional<Connection>, Optional<Disposable>> make;
                make = makeTestCaseListener(hdlr, umb);
                CIBusListener<DefaultResult> cbl = make.first;

                String address = String.format("Consumer.%s.%s", cbl.getClientID(), CIBusListener.TOPIC);
                ImportArgs args = new ImportArgs(cfg, address);
                logger.info("Sending testcase import request");

                // Run the import request (the post) in a worker verticle in case the http takes a long time
                // TODO: make a post() using vertx client instead of blocking httpclient
                logger.info("Launching worker verticle to make TestCase import");
                WorkerExecutor executor = vertx.createSharedWorkerExecutor("TestCaseImport.request");
                this.executeImport(executor, args, cbl, address, jo)
                    .subscribe((JsonObject resp) -> {
                        logger.info(resp.toString());
                    });
            }, err -> {
                logger.error("Failed uploading necessary files");
                JsonObject jo = new JsonObject();
                jo.put("result", "error");
                jo.put("message", "Failed uploading necessary files");
                ws.writeFinalTextFrame(jo.encode());
            });
    }

    private void testcaseImport(ServerWebSocket ws) {
        logger.info("In testcaseImport");

        Observable<TestCaseMessage> s$ = this.makeWebsocketObservable(ws, TestCaseMessage.class);
        s$.map(TestCaseMessage::createTCImpData)
            .subscribe(data -> {
                JsonObject jo = new JsonObject();
                TestCaseConfig cfg = data.getConfig();
                File mappath = new File(data.getMapping());
                Map<String, Map<String, IdParams>> mapping = FileHelper.loadMapping(mappath);

                // TODO: Need to figure out a way to pass a handler to the UMB verticle and pass this off
                MessageHandler<DefaultResult> hdlr = testcaseImportHandler(cfg.getProject(),
                        data.getTestcasePath(), mapping, cfg.getTestcase());

                // Create a UMBListenerData object, so we can send messages over the event bus
                Selector sel = cfg.getTestcase().getSelector();
                UMBListenerData umb = createUMBListenerData(sel, TCIMPORT_TAG_PREFIX, TCIMPORT_BUS);

                // Bridge the JMS to the websocket
                int maxsize = 1024 * 1024;
                jmsToWebSocket(ws, umb);

                // Create the CIBusListener, the Connection (so we can close) and a way to cancel
                Tuple3<CIBusListener<DefaultResult>, Optional<Connection>, Optional<Disposable>> make;
                make = makeTestCaseListener(hdlr, umb);
                CIBusListener<DefaultResult> cbl = make.first;

                String address = String.format("Consumer.%s.%s", cbl.getClientID(), CIBusListener.TOPIC);
                ImportArgs args = new ImportArgs(cfg, address);
                logger.info("Sending testcase import request");

                // Run the import request (the post) in a worker verticle in case the http takes a long time
                // TODO: make a post() using vertx client instead of blocking httpclient
                logger.info("Launching worker verticle to make TestCase import");
                WorkerExecutor executor = vertx.createSharedWorkerExecutor("TestCaseImport.request");
                this.executeTCImport(executor, args, cbl, address, jo)
                        .subscribe((JsonObject resp) -> {
                            logger.info(resp.getString("result"));
                            // TODO: Dig into this json data, and get the JobID
                        });

                jo.put("result", "Waiting for reply from Polarion");
                String msg = jo.encode();
                if (msg.length() < maxsize) {
                    ws.writeFinalTextFrame(msg);
                }
                else {
                    Buffer buff = Buffer.buffer(msg);
                    bufferToWebSocket(ws, buff, maxsize);
                }

            }, err -> {
                logger.error("Failed uploading necessary files");
                JsonObject jo = new JsonObject();
                jo.put("result", "error");
                jo.put("message", "Failed uploading necessary files");
                ws.writeFinalTextFrame(jo.encode());
            });
    }

    private void queueBrowser(RoutingContext rc) {
        logger.info("In testcaseImport");
        HttpServerRequest req = rc.request();

        String queueType = req.getParam("queuetype"); // completed or queue
        String importType = req.getParam("importtype"); // testcase or xunit
        String jobId = req.getParam("jobid");
    }


    private String getTCImportResult(Optional<MessageResult<DefaultResult>> maybe) {
        String msg = "No Results";
        if (maybe.isPresent()) {
            MessageResult<DefaultResult> mr = maybe.get();
            if (mr.info != null)
                msg = mr.info.getText();
        }
        return msg;
    }

    private void umbListener(ServerWebSocket ws) {
        logger.info("In umbListener");
        SocketAddress add = ws.remoteAddress();

        Observable<TextMessage> u$ = this.makeWebsocketObservable(ws, TextMessage.class);
        u$.subscribe((TextMessage tm) -> {
            UMBListenerData next = Serializer.from(UMBListenerData.class, tm.getData());
            next.clientAddress = add.toString();
            ObjectMapper mapper = new ObjectMapper();
            String body = mapper.writeValueAsString(next);
            String action = next.getAction();
            String msgAddress = next.getBusAddress();
            String umbAddress = String.format("umb.messages.%s", action);
            String clientID = String.format("%s-%s", next.getTag(), next.clientAddress);

            if (!this.sockets.containsKey(clientID))
                this.sockets.put(clientID, ws);

            // For closing the UMB listener
            next.setAction(UMB.svcStop);
            String closeRequest = mapper.writeValueAsString(next);
            ws.closeHandler(hdlr -> {
                logger.info("==========================");
                logger.info(String.format("Closing UMB websocket bridge for %s\n%s", clientID, closeRequest));
                logger.info("==========================");
                this.sockets.remove(clientID);
                this.bus.publish(umbAddress, closeRequest);
            });

            // Tell the UMB Verticle to start listening for messages.  It is listening
            // for requests on "umb.messages.start" request address
            this.bus.publish(umbAddress, body);
            // The UMB Verticle will now start sending messages from the UMB
            // to the event bus on address defined in next.getBusAddress()
            // TODO: Write an unregister handle that is called when action = stop
            // This will also close the websocket
            // FIXME:  Check if the length of the message is greater than the frame length, otherwise we will
            // get an exception
            this.bus.consumer(msgAddress, (Message<String> msg) -> {
                if (!this.sockets.containsKey(clientID))
                    return;
                ws.resume();
                String item = msg.body();
                try {
                    ws.writeFinalTextFrame(item);
                }
                catch (IllegalStateException ise) {
                    logger.error(ise.getMessage());
                }
            });
        });
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
            logger.info(body);
            // Send message on event bus to the APITestSuite Verticle
            String address = APITestSuite.class.getCanonicalName();
            logger.info("Sending message to: " + address);
            // FIXME: Tried using rxSend() but got an error that no consumer was registered
            this.bus.send("APITestSuite", body);
            // this.bus.rxSend("APITestSuite", body);
            JsonObject jo = new JsonObject();
            jo.put("result", "Kicking off tests");
            req.response().end(jo.encode());
        });
    }

    private void hello(RoutingContext rc) {
        HttpServerRequest req = rc.request();
        JsonObject jo = new JsonObject();
        String remote = req.remoteAddress().toString();
        jo.put("result", "congratulations, server responded");
        jo.put("client-ip", remote);
        Optional<String> headers = req.headers().names().stream().reduce((acc, n) -> {
            acc += acc + "," + n;
            return acc;
        });
        headers.ifPresent(h -> {
            jo.put("headers", h);
        });
        req.response().end(jo.encode());
    }

    /**
     * This function will setup TLS security on the server.
     *
     * FIXME: For testing, we will use a self-signed certificate, but look into using a RHCA assigned TLS cert for
     * production use
     *
     * @param opts Options that need to passed to the server.  TLS option assigned to it
     * @return The same HttpServerOptions as passed in
     */
    public HttpServerOptions setupTLS(HttpServerOptions opts) {
        String keystore = this.config().getString("keystore-path");
        String keypass = this.config().getString("keystore-password");
        if (opts == null)
            opts = new HttpServerOptions();
        return opts
                .setSsl(true)
                .setKeyStoreOptions(new JksOptions().setPath(keystore).setPassword(keypass));
    }

    private static void test_() {
        String project = "RedHatEnterpriseLinux7";
        String xmlpath = "/home/stoner/testcases.xml";
        String name = "rhsm.cli.tests.BashCompletionTests.testBashCompletionFull";
        String id = "RHEL7-51406";
        String mappath = "/home/stoner/mapping-copy.json";

        JAXBReporter rep = new JAXBReporter();
        Optional<Testcases> isTcs = IJAXBHelper.unmarshaller(Testcases.class, new File(xmlpath),
                rep.getXSDFromResource(Testcases.class));
        Testcases tcs = isTcs.orElseThrow(() -> new Error("Could not unmarshall testcases xml file"));

        Map<String, Map<String, IdParams>> mapping = FileHelper.loadMapping(new File(mappath));
        searchTestCases(project, tcs, name, id, mapping);
        if (!mapping.containsKey(name))
            System.err.println("new method not in map");
        else {
            IdParams idp = mapping.get(name).get(project);
            System.out.println(idp.getId());
        }

        name = "foobar";
        id = "RHEL7-12345";
        searchTestCases(project, tcs, name, id, mapping);
    }
}
