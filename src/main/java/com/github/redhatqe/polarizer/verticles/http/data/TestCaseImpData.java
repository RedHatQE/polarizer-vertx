package com.github.redhatqe.polarizer.verticles.http.data;

import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TestCaseImpData extends PolarizerData {
    private String testcasePath;
    private TestCaseConfig config;
    private String mapping;

    private String[] _done = {"testcase", "tcargs", "mapping"};
    public Set<String> done = new HashSet<>(Arrays.asList(_done));

    public TestCaseImpData(UUID id) {
        super(id);
    }

    /**
     * This is a hacky way of doing a reduction.  The first object (this) is the accumulator, and the arg passed to
     * merge is the object that will be reduced into the first.
     *
     * FIXME: This is really brittle, there has to be a better way to do this
     *
     * @param other
     * @return
     */
    public TestCaseImpData merge(TestCaseImpData other) {
        if (other.completed != null)
            this.completed.addAll(other.completed);
        if (other.testcasePath != null) {
            this.testcasePath = other.testcasePath;
        }
        if (other.config != null) {
            this.config = other.config;
            if (this.mapping != null)
                this.config.setMapping(this.mapping);
        }
        if (other.mapping != null) {
            this.mapping = other.mapping;
            if (this.config != null && this.config.getMapping() == null)
                this.config.setMapping(this.mapping);
        }
        return this;
    }

    public String getTestcasePath() {
        return testcasePath;
    }

    public void setTestcasePath(String testcasePath) {
        this.testcasePath = testcasePath;
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
        return this.completed.containsAll(done);
    }

    @Override
    public int size() {
        return this.completed.size();
    }
}
