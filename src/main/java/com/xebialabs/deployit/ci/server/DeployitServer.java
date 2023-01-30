package com.xebialabs.deployit.ci.server;

import java.util.List;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.booter.remote.DeployitCommunicator;
import com.xebialabs.deployit.ci.JenkinsDeploymentOptions;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.dto.ServerInfo;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;

public interface DeployitServer {

    public static final int DEFAULT_POOL_SIZE = 25;
    public static final int DEFAULT_SOCKET_TIMEOUT = 60000;

    void setConnectionPoolSize(int poolSize);

    void setSocketTimeout(int poolSize);

    List<String> search(String type);

    List<String> search(String type, String namePattern);

    ConfigurationItem importPackage(String darFile);

    void deploy(String deploymentPackage, String environment,  JenkinsDeploymentOptions deploymentOptions, JenkinsDeploymentListener listener);

    DeployitCommunicator newCommunicator();

    DeployitDescriptorRegistry getDescriptorRegistry();

    BooterConfig getBooterConfig();

    void reload();

    ServerInfo getServerInfo();

    String getRegistryVersion();
}
