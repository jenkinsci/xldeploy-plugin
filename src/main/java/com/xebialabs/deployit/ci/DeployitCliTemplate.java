package com.xebialabs.deployit.ci;

import com.xebialabs.deployit.client.ConnectionOptions;
import com.xebialabs.deployit.client.DeployitCli;
import com.xebialabs.deployit.client.Descriptors;

import hudson.PluginManager;
import hudson.model.Hudson;
import hudson.util.Secret;
import jenkins.model.Jenkins;

/**
 * Manage the Classloader when using the resteasy stack, else ClassNotFoundException.
 */
public class DeployitCliTemplate {

    private String deployitServerUrl;
    private String deployitClientProxyUrl;

    private Credential credential;

    public DeployitCliTemplate(String deployitServerUrl, String deployitClientProxyUrl, Credential credential) {
        this.deployitServerUrl = deployitServerUrl;
        this.deployitClientProxyUrl = deployitClientProxyUrl;
        this.credential = credential;
    }

    public DeployitCliTemplate(String deployitServerUrl, String deployitClientProxyUrl, String username, Secret password) {
        this(deployitServerUrl, deployitClientProxyUrl, new Credential(username, username, password));
    }

    public <T> T perform(DeployitCliCallback<T> callback) {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader origClassLoader = currentThread.getContextClassLoader();

        try {
            final ClassLoader pluginClassLoader = Jenkins.getInstance().getPluginManager().getPlugin("deployit-plugin").classLoader;
            currentThread.setContextClassLoader(pluginClassLoader);
            return callback.call(getCli());
        } finally {
            currentThread.setContextClassLoader(origClassLoader);
        }
    }

    private synchronized DeployitCli getCli() {
        return new DeployitCli(new ConnectionOptions(deployitServerUrl, deployitClientProxyUrl, credential.username, credential.password.getPlainText()));
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public String getDeployitServerUrl() {
        return deployitServerUrl;
    }

    public String getDeployitClientProxyUrl() {
        return deployitClientProxyUrl;
    }

    public Credential getCredential() {
        return credential;
    }

    public Descriptors getDescriptors() {
        return perform(new DeployitCliCallback<Descriptors>() {
                public Descriptors call(DeployitCli cli) {
                        return cli.getDescriptors();
                }
            });
    }
}
