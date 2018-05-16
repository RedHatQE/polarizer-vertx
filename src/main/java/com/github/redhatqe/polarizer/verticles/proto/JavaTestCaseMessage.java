package com.github.redhatqe.polarizer.verticles.proto;

import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.verticles.http.data.TestCaseDataFromWS;
import com.github.redhatqe.polarizer.verticles.http.data.TestCaseImpData;
import io.vertx.reactivex.core.buffer.Buffer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class JavaTestCaseMessage extends BinaryMessage {
    private static String[] _done = {"jar", "tcargs", "mapping"};
    public static Set<String> done = new HashSet<>(Arrays.asList(_done));

    public JavaTestCaseMessage() {
        super();
    }

    public JavaTestCaseMessage(String op, String type, Buffer data, String tag, Boolean ack) {
        super(op, type, data, tag, ack);
    }

    public static Map<String, String> merge(Map<String, String> acc, TestCaseMessage next) {
        Map<String, String> accumulated = new HashMap<>(acc);
        accumulated.put(next.getType(), next.getData());
        return accumulated;
    }

    public static TestCaseImpData createTCImpData(BinaryMessage tcm) throws IOException {
        UUID id = UUID.randomUUID();
        TestCaseImpData data = new TestCaseImpData(id);
        Buffer rawData = tcm.getData();
        return data;
    }
}
