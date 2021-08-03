package com.xebialabs.deployit.ci.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginLogger {

    private static final Logger LOG = LoggerFactory.getLogger(PluginLogger.class);

    private static final PluginLogger instance = new PluginLogger();

    public static PluginLogger getInstance() {
        return instance;
    }

    private boolean verbose;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void debug(String message) {
        if (verbose) {
            LOG.info(message);
        }
    }

    public void debug(String message, Object... arg) {
        if (verbose) {
            LOG.info(message, arg);
        }
    }

    public void info(String message) {
        LOG.info(message);
    }

    public void info(String message, Object... arg) {
        LOG.info(message, arg);
    }

    public void warn(String message) {
        LOG.warn(message);
    }

    public void warn(String message, Object... arg) {
        LOG.warn(message, arg);
    }
}
