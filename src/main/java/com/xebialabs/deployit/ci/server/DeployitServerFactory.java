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
    private static final int HTTPS_PORT = 443;
    private static final int HTTP_PORT = 80;

    public static boolean validConnection(String serverUrl, String proxyUrl, String username, String password, int connectionPoolSize, int socketTimeout) throws IllegalStateException {
        newInstance(serverUrl, proxyUrl, username, password, connectionPoolSize, socketTimeout).newCommunicator();  //throws IllegalStateException on failure.
        return true;
    }

    public static DeployitServer newInstance(String serverUrl, String proxyUrl, String username, String password, int connectionPoolSize, int socketTimeout) {
        return newInstance(getBooterConfig(serverUrl, proxyUrl, username, password, connectionPoolSize, socketTimeout));
    }

    public static DeployitServer newInstance(BooterConfig booterConfig) {
        DeployitServer server = new DeployitServerImpl(booterConfig);
        return Reflection.newProxy(DeployitServer.class, new PluginFirstClassloaderInvocationHandler(server));
    }

    public static BooterConfig getBooterConfig(String serverUrl, String proxyUrl, String username, String password, int connectionPoolSize, int socketTimeout) {
        BooterConfig.Builder builder = BooterConfig.builder();
        URL url;
        int port;
        try {
            url = new URL(serverUrl);
        } catch (MalformedURLException e) {
            throw new DeployitPluginException("MalformedURLException", e);
        }
        builder.withHost(url.getHost()).withPort(url.getPort());
        if ("https".equalsIgnoreCase(url.getProtocol())) {
            builder.withProtocol(BooterConfig.Protocol.HTTPS);
            builder.withPort(validateDefaultPort(url.getPort(), HTTPS_PORT));

        } else {
            builder.withPort(validateDefaultPort(url.getPort(), HTTP_PORT));
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
        builder.withConnectionPoolSize(connectionPoolSize);
        builder.withSocketTimeout(socketTimeout);
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

    public static int validateDefaultPort(int urlPort, int defaultPort) {
        if (urlPort == -1) {
            return defaultPort;
        }
        return urlPort;
    }
}