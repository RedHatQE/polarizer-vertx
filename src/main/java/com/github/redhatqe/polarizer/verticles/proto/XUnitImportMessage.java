package com.github.redhatqe.polarizer.verticles.proto;

import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.verticles.http.data.XUnitData;
import com.github.redhatqe.polarizer.verticles.http.data.XUnitDataFromWS;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class XUnitImportMessage extends TextMessage {
    private static String[] _done = {"xunit", "xargs"};
    public static Set<String> done = new HashSet<>(Arrays.asList(_done));

    public  XUnitImportMessage() {
        super();
    }

    public XUnitImportMessage(String op, String type, String data, String tag, Boolean ack) {
        super(op, type, data, tag, ack);
    }

    public static XUnitData createXUnitData(XUnitImportMessage msg) throws IOException {
        UUID id = UUID.randomUUID();
        XUnitData data = new XUnitData(id);
        String rawData = msg.getData();
        XUnitDataFromWS xud = Serializer.from(XUnitDataFromWS.class, rawData);

        Path path = FileHelper.makeTempPath("/tmp", "polarion-xunit-", ".xml", null);
        FileHelper.writeFile(path, xud.getXunit());
        data.setXunitPath(path.toString());

        XUnitConfig cfg = Serializer.from(XUnitConfig.class, xud.getXargs());
        cfg.setCurrentXUnit(path.toString());
        data.setConfig(cfg);

        return data;
    }
}
