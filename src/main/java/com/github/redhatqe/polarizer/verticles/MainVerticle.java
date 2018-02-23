package com.github.redhatqe.polarizer.verticles;


import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.verticles.http.Polarizer;
import com.github.redhatqe.polarizer.verticles.http.config.PolarizerVertConfig;
import com.github.redhatqe.polarizer.verticles.messaging.UMB;
import com.github.redhatqe.polarizer.verticles.tests.APITestSuite;
import com.github.redhatqe.polarizer.verticles.tests.WebSocketClient;
import com.github.redhatqe.polarizer.verticles.tests.config.APITestSuiteConfig;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class MainVerticle extends AbstractVerticle {
    private static Logger logger = LogManager.getLogger(Polarizer.class.getSimpleName());
    public static final String POLARIZER_VERT = Polarizer.class.getCanonicalName();
    public static final String POLARIZER_ENV = "POLARIZER_MAIN_CONFIG";
    public static final String POLARIZER_PROP = "polarizer.main.config";
    public static final String TEST_VERT = APITestSuite.class.getCanonicalName();
    public static final String TEST_ENV = "POLARIZER_TEST_CONFIG";
    public static final String TEST_PROP = "polarizer.test.config";
    public static final String UMB_VERTICLE = UMB.class.getCanonicalName();


    private Disposable dep;

    public void start() throws IOException {
        VertxOptions opts = new VertxOptions();
        opts.setBlockedThreadCheckInterval(120000);
        logger.info("Starting MainVerticle");
        //Vertx.vertx(opts);
        DeploymentOptions pOpts = this.setupConfig(PolarizerVertConfig.class, POLARIZER_ENV, POLARIZER_PROP);
        DeploymentOptions tOpts = this.setupConfig(APITestSuiteConfig.class, TEST_ENV, TEST_PROP);

        Single<String> polarizerDeployer = vertx.rxDeployVerticle(POLARIZER_VERT, pOpts);
        dep = polarizerDeployer.subscribe(succ -> {
                    // Start the APITestSuite verticle once the Polarizer verticle is running
                    Single<String> deployed = vertx.rxDeployVerticle(TEST_VERT, tOpts);
                    deployed.subscribe(next -> logger.info("APITestSuite Verticle is now deployed"),
                                       err -> logger.error(err.getMessage()));
                    Single<String> umbvert = vertx.rxDeployVerticle(UMB_VERTICLE);
                    // Start the UMB verticle once Polarizer verticle is running
                    umbvert.subscribe(next -> {
                        logger.info("UMB Verticle is now deployed");
                    }, err -> {
                        logger.error("Failed to deploy UMB Verticle");
                    });

                    logger.info("Polarizer was deployed");



                    Single<String> wsclient = vertx.rxDeployVerticle(WebSocketClient.class.getCanonicalName());
                    wsclient.subscribe(n -> {
                        logger.info("Starting ws client");
                    });


            },
            err -> logger.error("Failed to deploy Polarizer\n" + err.getMessage()));
    }

    public void stop() {
        this.dep.dispose();
    }

    private <T> DeploymentOptions setupConfig(Class<T> cls, String env, String prop) throws IOException {
        DeploymentOptions opts = new DeploymentOptions();
        String envPath = System.getenv(env);
        String path = System.getProperty(prop);
        String home = System.getProperty("user.home");
        String defaultCfg = Paths.get(home, ".polarizer", "polarizer-config.json").toString();
        String filePath = envPath != null ? envPath : path != null ? path : defaultCfg;
        File fpath = new File(filePath);
        T pCfg;
        if (fpath.exists()) {
            pCfg = Serializer.from(cls, fpath);
            opts.setConfig(JsonObject.mapFrom(pCfg));
        }

        return opts;
    }
}
