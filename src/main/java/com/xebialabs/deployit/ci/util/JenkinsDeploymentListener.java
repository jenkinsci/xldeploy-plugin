package com.xebialabs.deployit.ci.util;

import java.io.Serializable;
import org.jvnet.localizer.Localizable;

import com.xebialabs.deployit.client.logger.AbstractDeploymentListener;
import com.xebialabs.deployit.engine.packager.content.PackagingListener;

import hudson.model.BuildListener;

public class JenkinsDeploymentListener extends AbstractDeploymentListener implements PackagingListener, Serializable {

    private final BuildListener listener;
    private final boolean debug;

    public JenkinsDeploymentListener(BuildListener listener, boolean debug) {
        this.listener = listener;
        this.debug = debug;
    }

    public void info(Localizable localizable) {
        info(String.valueOf(localizable));
    }

    public void error(Localizable localizable) {
        error(String.valueOf(localizable));
    }

    public void handleDebug(String message) {
        if (debug)
            listener.getLogger().println("Debug: " + message);
    }

    public void handleInfo(String message) {
        listener.getLogger().println("Info : " + message);
    }

    public void handleTrace(String message) {
        listener.getLogger().println("Trace: " + message);
    }

    public void handleError(String message) {
        listener.error(message);
    }

    public void println(final String message) {
        info(message);
    }
}
