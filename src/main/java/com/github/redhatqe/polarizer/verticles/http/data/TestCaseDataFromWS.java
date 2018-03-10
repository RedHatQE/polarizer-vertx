package com.github.redhatqe.polarizer.verticles.http.data;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * When a TestCase import is sent over a websocket, the TextMessage data field is just a raw string.  We can parse
 * this to convert to this object
 */
public class TestCaseDataFromWS {
    @JsonProperty(required = true)
    private String tcargs;
    @JsonProperty(required = true)
    private String testcase;
    @JsonProperty(required = true)
    private String mapping;


    public String getTcargs() {
        return tcargs;
    }

    public void setTcargs(String tcargs) {
        this.tcargs = tcargs;
    }

    public String getTestcase() {
        return testcase;
    }

    public void setTestcase(String testcase) {
        this.testcase = testcase;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }
}
