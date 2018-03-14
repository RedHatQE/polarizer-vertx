
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.verticles.http.data.XUnitDataFromWS;
import com.github.redhatqe.polarizer.verticles.proto.TextMessage;
import com.github.redhatqe.polarizer.verticles.proto.UMBListenerData;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;


public class SerializerTests {
    public String xunit;
    public String xargs;
    public String testcase;
    public String tcargs;

    public SerializerTests() {
        String home = Paths.get(System.getProperty("user.home")).toString();
        xunit = Paths.get(home, "Projects/testpolarize/test-output/testng-polarion.xml").toString();
        xargs = Paths.get(home, "test-polarizer-xunit.json").toString();
        testcase = Paths.get(home, "Projects/testpolarize/testcases").toString();
        tcargs = Paths.get(home, "test-polarizer-testcase.json").toString();
    }

    @Test(groups={"serialization"},
            description="Tests json message can be serialized for xunit import")
    public void serializeXUnitWebSocket() throws IOException {
        String xunitXml = FileHelper.readFile(this.xunit);
        String xunitArgs = FileHelper.readFile(this.xargs);

        XUnitDataFromWS wsreq = new XUnitDataFromWS();
        wsreq.setXargs(xunitArgs);
        wsreq.setXunit(xunitXml);

        String reqAsString = Serializer.toJson(wsreq);

        TextMessage data = new TextMessage("xunit", "na", reqAsString, "unique", false);

        String raw = "{\\n  \"op\": \"xunit-import-ws\",\\n  \"type\": \"na\",\\n  \"tag\": \"xunit-import-99805787-4574-4abc-bdc8-572d35d16416\",\\n  \"ack\": true,\\n  \"data\": {\\n    \"xunit\": \"<?xml version=\\\\\"1.0\\\\\" encoding=\\\\\"UTF-8\\\\\" standalone=\\\\\"yes\\\\\"?>\\\\n<testsuites>\\\\n    <properties>\\\\n        <property name=\\\\\"polarion-user-id\\\\\" value=\\\\\"platformqe\\\\\"/>\\\\n        <property name=\\\\\"polarion-project-id\\\\\" value=\\\\\"PLATTP\\\\\"/>\\\\n        <property name=\\\\\"polarion-set-testrun-finished\\\\\" value=\\\\\"true\\\\\"/>\\\\n        <property name=\\\\\"polarion-dry-run\\\\\" value=\\\\\"false\\\\\"/>\\\\n        <property name=\\\\\"polarion-include-skipped\\\\\" value=\\\\\"false\\\\\"/>\\\\n        <property name=\\\\\"polarion-response-rhsm_qe\\\\\" value=\\\\\"xunit_importer\\\\\"/>\\\\n        <property name=\\\\\"polarion-custom-variant\\\\\" value=\\\\\"Compute\\\\\"/>\\\\n        <property name=\\\\\"polarion-custom-arch\\\\\" value=\\\\\"aarch64\\\\\"/>\\\\n        <property name=\\\\\"polarion-testrun-title\\\\\" value=\\\\\"Testing the reporter\\\\\"/>\\\\n        <property name=\\\\\"polarion-testrun-id\\\\\" value=\\\\\"whatthe\\\\\"/>\\\\n        <property name=\\\\\"polarion-testrun-template-id\\\\\" value=\\\\\"stoner test template\\\\\"/>\\\\n    </properties>\\\\n    <testsuite name=\\\\\"Sanity Test\\\\\" tests=\\\\\"5\\\\\" failures=\\\\\"1\\\\\" errors=\\\\\"1\\\\\" time=\\\\\"0.014\\\\\" skipped=\\\\\"1\\\\\">\\\\n        <testcase name=\\\\\"testBadProjectToTestCaseID\\\\\" time=\\\\\"0.003\\\\\" classname=\\\\\"com.github.redhatqe.rhsm.testpolarize.TestReq\\\\\" status=\\\\\"success\\\\\">\\\\n            <properties>\\\\n                <property name=\\\\\"polarion-testcase-id\\\\\" value=\\\\\"PLATTP-10202\\\\\"/>\\\\n            </properties>\\\\n        </testcase>\\\\n        <testcase name=\\\\\"testError\\\\\" time=\\\\\"0.0\\\\\" classname=\\\\\"com.github.redhatqe.rhsm.testpolarize.TestReq\\\\\">\\\\n            <error message=\\\\\"Just throwing an error\\\\\">com.github.redhatqe.rhsm.testpolarize.TestReq.testError(TestReq.java:96)\\\\nsun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\\\nsun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\\\nsun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\\\njava.lang.reflect.Method.invoke(Method.java:498)\\\\norg.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:86)\\\\norg.testng.internal.Invoker.invokeMethod(Invoker.java:643)\\\\norg.testng.internal.Invoker.invokeTestMethod(Invoker.java:820)\\\\norg.testng.internal.Invoker.invokeTestMethods(Invoker.java:1128)\\\\norg.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:129)\\\\norg.testng.internal.TestMethodWorker.run(TestMethodWorker.java:112)\\\\norg.testng.TestRunner.privateRun(TestRunner.java:782)\\\\norg.testng.TestRunner.run(TestRunner.java:632)\\\\norg.testng.SuiteRunner.runTest(SuiteRunner.java:366)\\\\norg.testng.SuiteRunner.runSequentially(SuiteRunner.java:361)\\\\norg.testng.SuiteRunner.privateRun(SuiteRunner.java:319)\\\\norg.testng.SuiteRunner.run(SuiteRunner.java:268)\\\\norg.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)\\\\norg.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)\\\\norg.testng.TestNG.runSuitesSequentially(TestNG.java:1244)\\\\norg.testng.TestNG.runSuitesLocally(TestNG.java:1169)\\\\norg.testng.TestNG.run(TestNG.java:1064)\\\\norg.testng.TestNG.privateMain(TestNG.java:1385)\\\\norg.testng.TestNG.main(TestNG.java:1354)\\\\n</error>\\\\n            <properties>\\\\n                <property name=\\\\\"polarion-testcase-id\\\\\" value=\\\\\"PLATTP-10203\\\\\"/>\\\\n            </properties>\\\\n        </testcase>\\\\n        <testcase name=\\\\\"testUpgrade\\\\\" time=\\\\\"0.0\\\\\" classname=\\\\\"com.github.redhatqe.rhsm.testpolarize.TestReq\\\\\" status=\\\\\"success\\\\\">\\\\n            <properties>\\\\n                <property name=\\\\\"polarion-testcase-id\\\\\" value=\\\\\"PLATTP-10068\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-name\\\\\" value=\\\\\"Sean\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-age\\\\\" value=\\\\\"44\\\\\"/>\\\\n            </properties>\\\\n        </testcase>\\\\n        <testcase name=\\\\\"testUpgrade\\\\\" time=\\\\\"0.0\\\\\" classname=\\\\\"com.github.redhatqe.rhsm.testpolarize.TestReq\\\\\" status=\\\\\"success\\\\\">\\\\n            <properties>\\\\n                <property name=\\\\\"polarion-testcase-id\\\\\" value=\\\\\"PLATTP-10068\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-name\\\\\" value=\\\\\"Toner\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-age\\\\\" value=\\\\\"0\\\\\"/>\\\\n            </properties>\\\\n        </testcase>\\\\n        <testcase name=\\\\\"testUpgradeNegative\\\\\" time=\\\\\"0.0\\\\\" classname=\\\\\"com.github.redhatqe.rhsm.testpolarize.TestReq\\\\\" status=\\\\\"success\\\\\">\\\\n            <properties>\\\\n                <property name=\\\\\"polarion-testcase-id\\\\\" value=\\\\\"PLATTP-9520\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-name\\\\\" value=\\\\\"Sean\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-age\\\\\" value=\\\\\"44\\\\\"/>\\\\n            </properties>\\\\n        </testcase>\\\\n        <testcase name=\\\\\"testUpgradeNegative\\\\\" time=\\\\\"0.0\\\\\" classname=\\\\\"com.github.redhatqe.rhsm.testpolarize.TestReq\\\\\">\\\\n            <failure>expected [44] but found [0]</failure>\\\\n            <properties>\\\\n                <property name=\\\\\"polarion-testcase-id\\\\\" value=\\\\\"PLATTP-9520\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-name\\\\\" value=\\\\\"Toner\\\\\"/>\\\\n                <property name=\\\\\"polarion-parameter-age\\\\\" value=\\\\\"0\\\\\"/>\\\\n            </properties>\\\\n        </testcase>\\\\n    </testsuite>\\\\n</testsuites>\\\\n\",\\n    \"xargs\": \"{\\\\n  \\\\\"project\\\\\": \\\\\"PLATTP\\\\\",\\\\n  \\\\\"mapping\\\\\": \\\\\"\\\\\",\\\\n  \\\\\"servers\\\\\": {\\\\n    \\\\\"polarion\\\\\": {\\\\n      \\\\\"url\\\\\": \\\\\"https://polarion-devel.engineering.redhat.com/polarion\\\\\",\\\\n      \\\\\"user\\\\\": \\\\\"stoner\\\\\",\\\\n      \\\\\"password\\\\\": \\\\\"!ronM@N1968\\\\\"\\\\n    }\\\\n  },\\\\n  \\\\\"xunit\\\\\": {\\\\n    \\\\\"testrun\\\\\": {\\\\n      \\\\\"id\\\\\": \\\\\"\\\\\",\\\\n      \\\\\"title\\\\\": \\\\\"Sean Toner Polarize TestRun\\\\\",\\\\n      \\\\\"template-id\\\\\": \\\\\"sean toner test template\\\\\"\\\\n    },\\\\n    \\\\\"custom\\\\\": {\\\\n      \\\\\"test-suite\\\\\": {\\\\n        \\\\\"dry-run\\\\\": false,\\\\n        \\\\\"set-testrun-finished\\\\\": true,\\\\n        \\\\\"include-skipped\\\\\": false\\\\n      },\\\\n      \\\\\"properties\\\\\": {\\\\n        \\\\\"variant\\\\\": \\\\\"The template id to use for test runs\\\\\",\\\\n        \\\\\"arch\\\\\": \\\\\"x86_64\\\\\",\\\\n        \\\\\"plannedin\\\\\": \\\\\"\\\\\",\\\\n        \\\\\"jenkinsjobs\\\\\": \\\\\"Path to the jenkins job\\\\\",\\\\n        \\\\\"notes\\\\\": \\\\\"arbitrary field for notes\\\\\"\\\\n      }\\\\n    },\\\\n    \\\\\"endpoint\\\\\": \\\\\"/import/xunit\\\\\",\\\\n    \\\\\"selector\\\\\": {\\\\n      \\\\\"name\\\\\": \\\\\"rhsm_qe\\\\\",\\\\n      \\\\\"value\\\\\": \\\\\"xunit_importer\\\\\"\\\\n    },\\\\n    \\\\\"timeout\\\\\": 300000,\\\\n    \\\\\"enabled\\\\\": true\\\\n  }\\\\n}\\\\n\"\\n  }\\n}\n";
        XUnitDataFromWS wsd = Serializer.from(XUnitDataFromWS.class, raw);

        Assert.assertTrue(true);
    }

    public static void main(String[] args) throws IOException {
        TestCaseConfig cfg = Serializer.from(TestCaseConfig.class, new File(args[0]));
        String defs = cfg.getDefinitionsPath();
    }
}
