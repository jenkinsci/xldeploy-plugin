package com.xebialabs.deployit.ci.server;

import java.util.Collections;
import java.util.List;

import com.xebialabs.deployit.engine.api.dto.ServerInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.reflect.Reflection;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.booter.remote.DeployitCommunicator;
import com.xebialabs.deployit.booter.remote.RemoteBooter;
import com.xebialabs.deployit.booter.remote.client.DeployitRemoteClient;
import com.xebialabs.deployit.ci.JenkinsDeploymentOptions;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.DeploymentService;
import com.xebialabs.deployit.engine.api.RepositoryService;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.dto.ConfigurationItemId;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;

import static java.lang.String.format;

public class DeployitServerImpl implements DeployitServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeployitServerImpl.class);

    private static final int SOCKET_TIMEOUT = 60000;
    private BooterConfig booterConfig;
    private DeployitDescriptorRegistry descriptorRegistry;
    private int poolSize;

    DeployitServerImpl(BooterConfig booterConfig) {
        this.booterConfig = booterConfig;
        BooterConfig newBooterConfig = BooterConfig.builder(booterConfig)
            .withConnectionPoolSize(poolSize)
            .withHttpRequestInterceptor(new PreemptiveAuthenticationInterceptor())
            .withSocketTimeout(SOCKET_TIMEOUT)
            .build();
        this.descriptorRegistry = Reflection.newProxy(DeployitDescriptorRegistry.class,
                new PluginFirstClassloaderInvocationHandler(new DeployitDescriptorRegistryImpl(newBooterConfig)));
    }

    private DeployitCommunicator getCommunicator() {
        return getDescriptorRegistry().getCommunicator();
    }

    @Override
    public void setConnectionPoolSize(final int poolSize) {
        this.poolSize = poolSize;
    }

    @Override
    public List<String> search(String type) {
        return search(type, null);
    }

    @Override
    public List<String> search(String type, String namePattern) {
        LOGGER.debug("search " + type);
        try {

            List<ConfigurationItemId> result = getCommunicator().getProxies().getRepositoryService().query(getDescriptorRegistry().typeForName(type), null, null, namePattern, null, null, 0, -1);
            return Lists.transform(result, new Function<ConfigurationItemId, String>() {
                @Override
                public String apply(ConfigurationItemId input) {
                    return input.getId();
                }
            });
        } catch (Exception e) {
            LOGGER.error(format("search fails for %s", type), e);
        }
        return Collections.emptyList();
    }

    @Override
    public ConfigurationItem importPackage(final String darFile) {
        DeployitCommunicator communicator = getCommunicator();
        ConfigurationItem ci = new DeployitRemoteClient(communicator).importPackage(darFile);
        return ci;
    }

    @Override
    public void deploy(String deploymentPackage, String environment,  JenkinsDeploymentOptions deploymentOptions, JenkinsDeploymentListener listener) {
        DeploymentService deploymentService = getCommunicator().getProxies().getDeploymentService();
        TaskService taskService = getCommunicator().getProxies().getTaskService();
        RepositoryService repositoryService = getCommunicator().getProxies().getRepositoryService();
        new DeployCommand(deploymentService, taskService, repositoryService, deploymentOptions, listener).deploy(deploymentPackage, environment);
    }


    @Override
    public DeployitCommunicator newCommunicator() {
        // TODO ilx : remove this and go through registry!
        return getCommunicator();
    }

    @Override
    public DeployitDescriptorRegistry getDescriptorRegistry() {
        return descriptorRegistry;
    }

    @Override
    public BooterConfig getBooterConfig() {
        return booterConfig;
    }

    @Override
    public void reload() {
        getDescriptorRegistry().reload();
    }

    @Override
    public ServerInfo getServerInfo() {
        return getCommunicator().getProxies().getServerService().getInfo();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DeployitServerImpl that = (DeployitServerImpl) o;

        return ((booterConfig == null && that.booterConfig == null) ||
                (booterConfig != null && booterConfig.equals(that.booterConfig)));
    }

    @Override
    public int hashCode() {
        return booterConfig != null ? booterConfig.hashCode() : 0;
    }

}