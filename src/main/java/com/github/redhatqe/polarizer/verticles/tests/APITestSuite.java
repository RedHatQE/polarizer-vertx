package com.github.redhatqe.polarizer.verticles.tests;

import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.verticles.tests.config.APITestSuiteConfig;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.report.ReportOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.ext.unit.TestCompletion;
import io.vertx.reactivex.ext.unit.TestContext;
import io.vertx.reactivex.ext.unit.TestSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;


public class APITestSuite extends AbstractVerticle {
    private TestSuite suite;
    private Logger logger = LogManager.getLogger(APITestSuite.class.getSimpleName());
    private int port;
    private String host;
    private APITestSuiteConfig sconfig;
    private EventBus bus;
    private MessageConsumer<String> testObserver;

    public void start() {
        bus = vertx.eventBus();
        suite = TestSuite.create("API Tests");
        logger.info(String.format("Bringing up %s Verticle", APITestSuite.class.getSimpleName()));

        port = this.config().getInteger("port", 9000);
        host = this.config().getString("host", "localhost");
        // TODO: Make a service endpoint on Polarizer and have it send msg on Event bus
        this.registerEventBus();
        this.bus.consumer("APITestSuite-stop", (Message<String> msg) -> {
            logger.info("Got APITestSuite-stop message");
            this.testObserver.unregister();
        });
    }

    private void registerEventBus() {
        String address = APITestSuite.class.getCanonicalName();
        logger.info(String.format("Registering %s on event bus", address));
        this.testObserver = this.bus.consumer("APITestSuite");
        this.testObserver.handler((Message<String> msg) -> {
            String content = msg.body();
            try {
                this.sconfig = Serializer.from(APITestSuiteConfig.class, content);
                if (sconfig.validate()) {
                    logger.info("Launching tests");
                    this.test();
                }
                else
                    logger.error("Some of the files in the test configuration do not exist");
            } catch (IOException e) {
                logger.error("Could not deserialize configuration file");
            }
        });
        String vertName = APITestSuite.class.getSimpleName();
        logger.info(String.format("%s Verticle now registered to event bus on address %s", vertName, address));
    }

    private Callback<JsonNode> defaultCallBack(String ept, TestContext ctx) {
        return new Callback<JsonNode>() {
            @Override
            public void completed(HttpResponse<JsonNode> response) {
                int status = response.getStatus();
                JsonNode node = response.getBody();
                logger.info(String.format("Got status of: %d", status));
                logger.info(node.toString());
            }

            @Override
            public void failed(UnirestException e) {
                logger.error(String.format("Request to %s failed", ept));
            }

            @Override
            public void cancelled() {
                logger.warn("Request was cancelled");
            }
        };
    }

    /**
     * Actually executes the test
     */
    public void test() {
        String curr = Paths.get(".").toAbsolutePath().normalize().toString();
        // Report junit files to the current directory/test-output
        String testOut = curr + "/test-output";
        File cwd = new File(testOut);
        if (!cwd.exists() && !cwd.mkdirs()) {
            this.logger.error(String.format("Can not generate report file: directory %s is missing", curr));
            return;
        }
        this.logger.info("================ Starting tests ================");
        //suite.test("basic xunit generate test", this.testXunitGenerate());
        //suite.test("second xunit generate test", this.testXunitGenerate());
        //suite.test("basic xunit import test", this.testXunitImport());
        //suite.test("second xunit import test", this.testXunitImport());
        suite.test("tests testcase mapper endpoint", this.testTCMapper());
        //suite.test("tests testcase import endpoint", this.testTestCaseImport());

        ReportOptions consoleReport = new ReportOptions()
                .setTo("console");

        ReportOptions junitReport = new ReportOptions()
                .setTo(String.format("file:%s/test-output", curr))
                .setFormat("junit");

        TestCompletion results = suite.run(this.vertx, new TestOptions()
                .addReporter(consoleReport)
                .addReporter(junitReport)
        );
    }

    private Handler<TestContext> testXunitImport() {
        return tctx -> {
            String xunit = this.sconfig.getXunit().getImporter().getXml();
            String xargs = this.sconfig.getXunit().getImporter().getArgs();
            if (xunit == null || xargs == null) {
                tctx.fail("No arguments provided for test");
                return;
            }
            // Unfortunately, the vertx web client doesn't support multipart file, so lets use unirest
            Unirest.post(String.format("http://%s:%d/xunit/import", this.host, this.port))
                    .header("accept", "application/json")
                    .field("xunit", new File(xunit))
                    .field("xargs", new File(xargs))
                    .asJsonAsync(this.defaultCallBack("/xunit/import", tctx));
            tctx.assertTrue(1 == 1);
        };
    }

    private Handler<TestContext> testXunitGenerate() {
        return ctx -> {
            String xunit = this.sconfig.getXunit().getGenerate().getFocus();
            String xargs = this.sconfig.getXunit().getGenerate().getArgs();
            String mapping = this.sconfig.getXunit().getGenerate().getMapping();

            String url = String.format("http://%s:%d/xunit/generate", this.host, this.port);
            logger.info( String.format("\nMaking request to %s with\nxunit: %s\nxargs: %s\nmapping: %s"
                       , url, xunit, xargs, mapping));
            Unirest.post(url)
                    .header("accept", "application/json")
                    .field("xunit", new File(xunit))
                    .field("xargs", new File(xargs))
                    .field("mapping", new File(mapping))
                    .asJsonAsync(this.defaultCallBack("/xunit/generate", ctx));
            ctx.assertTrue(true);
        };
    }

    private Handler<TestContext> testTestCaseImport() {
        return ctx -> {
            String testcase = this.sconfig.getTestcase().getImporter().getXml();
            String tcargs = this.sconfig.getTestcase().getImporter().getArgs();
            String mapping = this.sconfig.getTestcase().getImporter().getMapping();

            String url = String.format("http://%s:%d/testcase/import", this.host, this.port);
            logger.info( String.format("\nMaking request to %s with\ntestcase: %s\ntcargs: %s"
                    , url, testcase, tcargs));
            Unirest.post(url)
                    .header("accept", "application/json")
                    .field("testcase", new File(testcase))
                    .field("tcargs", new File(tcargs))
                    .field("mapping", new File(mapping))
                    .asJsonAsync(this.defaultCallBack("/testcase/import", ctx));
            ctx.assertTrue(true);
        };
    }

    private Handler<TestContext> testTCMapper() {
        return ctx -> {
            String focus = this.sconfig.getTestcase().getMapper().getFocus();
            String tcargs = this.sconfig.getTestcase().getMapper().getArgs();
            String mapping = this.sconfig.getTestcase().getMapper().getMapping();

            String url = String.format("http://%s:%d/testcase/mapper", this.host, this.port);
            logger.info( String.format("\nMaking request to %s with\nfocus: %s\ntcargs: %s\nmapping: %s"
                    , url, focus, tcargs, mapping));
            Unirest.post(url)
                    .header("accept", "application/json")
                    .field("jar", new File(focus))
                    .field("tcargs", new File(tcargs))
                    .field("mapping", new File(mapping))
                    .asJsonAsync(this.defaultCallBack("/testcase/mapper", ctx));
            ctx.assertTrue(true);
        };
    }
}
