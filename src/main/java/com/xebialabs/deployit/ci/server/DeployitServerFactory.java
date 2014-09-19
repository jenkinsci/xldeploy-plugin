package com.xebialabs.deployit.ci.server;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.reflect.Reflection;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.ci.DeployitPluginException;

public class DeployitServerFactory {
    public static boolean validConnection(String serverUrl, String proxyUrl, String username, String password) throws IllegalStateException {
        newInstance(serverUrl, proxyUrl, username, password).newCommunicator();  //throws IllegalStateException on failure.
        return true;
    }

    public static DeployitServer newInstance(String serverUrl, String proxyUrl, String username, String password) {
        return newInstance(getBooterConfig(serverUrl, proxyUrl, username, password));
    }

    public static DeployitServer newInstance(BooterConfig booterConfig) {
        DeployitServerImpl server = new DeployitServerImpl(booterConfig);
        return Reflection.newProxy(DeployitServer.class, new PluginFirstClassloaderInvocationHandler(server));
    }

    public static BooterConfig getBooterConfig(String serverUrl, String proxyUrl, String username, String password) {
        BooterConfig.Builder builder = BooterConfig.builder();
        URL url;
        try {
            url = new URL(serverUrl);
        } catch (MalformedURLException e) {
            throw new DeployitPluginException("MalformedURLException", e);
        }
        builder.withHost(url.getHost()).withPort(url.getPort());
        if ("https".equalsIgnoreCase(url.getProtocol())) {
            builder.withProtocol(BooterConfig.Protocol.HTTPS);
        }
        if (url.getPath().length() > 0) {
            builder.withContext(url.getPath().substring(1));
        }
        if (!Strings.isNullOrEmpty(proxyUrl)) {
            URI proxyUri = URI.create(proxyUrl);
            builder.withProxyHost(proxyUri.getHost());
            builder.withProxyPort(proxyUri.getPort());
        }
        builder.withCredentials(username, password);
        return builder.build();
    }

    public static String getNameFromId(String id) {
        String[] nameParts = id.split("/");
        return nameParts[nameParts.length - 1];
    }

    public static String getParentId(String id) {
        String[] nameParts = id.split("/");
        List<String> list = Lists.newArrayList(nameParts);
        if (list.size() > 1) {
            list.remove(nameParts.length - 1);
        }
        return Joiner.on("/").join(list);
    }
}