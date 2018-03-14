package com.github.redhatqe.polarizer.verticles.http.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class XUnitDataFromWS {
    @JsonProperty(required = true)
    private String xargs;
    @JsonProperty(required = true)
    private String xunit;

    public XUnitDataFromWS() {

    }

    public String getXargs() {
        return xargs;
    }

    public void setXargs(String xargs) {
        this.xargs = xargs;
    }

    public String getXunit() {
        return xunit;
    }

    public void setXunit(String xunit) {
        this.xunit = xunit;
    }
}
