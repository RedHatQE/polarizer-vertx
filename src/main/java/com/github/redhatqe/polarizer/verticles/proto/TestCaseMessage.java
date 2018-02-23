package com.github.redhatqe.polarizer.verticles.proto;

import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.verticles.http.data.TestCaseImpData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;


public class TestCaseMessage extends TextMessage {
    private static String[] _done = {"testcase", "tcargs", "mapping"};
    public static Set<String> done = new HashSet<>(Arrays.asList(_done));

    public TestCaseMessage() {
        super();
    }

    public TestCaseMessage(String op, String type, String data, String tag, Boolean ack) {
        super(op, type, data, tag, ack);
    }

    public static Map<String, String> merge(Map<String, String> acc, TestCaseMessage next) {
        Map<String, String> accumulated = new HashMap<>(acc);
        accumulated.put(next.getType(), next.getData());
        return accumulated;
    }

    public static TestCaseImpData createTCImpData(Map<String, String> coll) throws IOException {
        UUID id = UUID.randomUUID();
        TestCaseImpData data = new TestCaseImpData(id);
        data.setCompleted(coll.keySet());

        Path path = FileHelper.makeTempPath("/tmp", "polarion-tc-", ".xml", null);
        FileHelper.writeFile(path, coll.get("testcase"));
        data.setTestcasePath(path.toString());

        Path mpath = FileHelper.makeTempPath("/tmp", "polarion-tcmap-", ".xml", null);
        FileHelper.writeFile(mpath, coll.get("mapping"));
        data.setMapping(mpath.toString());

        TestCaseConfig cfg = Serializer.from(TestCaseConfig.class, coll.get("tcargs"));
        data.setConfig(cfg);

        return data;
    }
}
