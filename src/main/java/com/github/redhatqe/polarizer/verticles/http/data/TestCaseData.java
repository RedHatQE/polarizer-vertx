package com.github.redhatqe.polarizer.verticles.http.data;

import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


public class TestCaseData extends PolarizerData {
    private String jarToCheck;
    private TestCaseConfig config;
    private String mapping;

    private String[] _done = {"jar", "mapping", "tcargs"};
    public Set<String> done = new HashSet<>(Arrays.asList(_done));

    public TestCaseData(UUID id) {
        super(id);
    }

    // FIXME: This is really ugly.  I think we should return a new copy with only the modifications needed.  This will
    // be slower as it will require a mem copy, but I think it's worth it
    public TestCaseData merge(TestCaseData other) {
        if (other.completed != null)
            this.completed.addAll(other.completed);
        if (other.jarToCheck != null) {
            this.jarToCheck = other.jarToCheck;
            if (this.config != null)
                this.config.setPathToJar(this.jarToCheck);
        }
        if (other.config != null) {
            this.config = new TestCaseConfig(other.config);
            if (this.jarToCheck != null) {
                this.config.setPathToJar(this.jarToCheck);
            }
            if (this.mapping != null) {
                this.config.setMapping(this.mapping);
            }
        }
        if (other.mapping != null) {
            this.mapping = other.mapping;
            if (this.config != null)
                this.config.setMapping(this.getMapping());
        }
        return this;
    }

    public String getJarToCheck() {
        return jarToCheck;
    }

    public void setJarToCheck(String jarToCheck) {
        this.jarToCheck = jarToCheck;
    }

    public TestCaseConfig getConfig() {
        return config;
    }

    public void setConfig(TestCaseConfig config) {
        this.config = config;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    @Override
    public boolean done() {
        return this.completed.containsAll(this.done);
    }

    @Override
    public int size() {
        return this.done.size();
    }
}
