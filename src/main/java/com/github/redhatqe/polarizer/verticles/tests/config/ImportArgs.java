package com.github.redhatqe.polarizer.verticles.tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImportArgs implements Validator {
    @JsonProperty(required = true)
    private String xml;
    @JsonProperty(required = true)
    private String args;
    @JsonProperty
    private String mapping;

    public ImportArgs() {

    }

    public Boolean validate() {
        List<File> files = new ArrayList<>();
        files.add(new File(this.xml));
        files.add(new File(this.args));
        return files.stream().allMatch(File::exists);
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }
}
