package com.xebialabs.deployit.ci.server;

import com.xebialabs.deployit.ci.DeployitPluginException;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.ControlService;
import com.xebialabs.deployit.engine.api.RepositoryService;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.dto.Control;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;

import java.util.Map;

public class ExecuteControlTaskCommand extends AbstractDeploymentCommand {

    private ControlService controlService;
    private RepositoryService repositoryService;

    protected ExecuteControlTaskCommand(TaskService taskService, JenkinsDeploymentListener listener, ControlService controlService, RepositoryService repositoryService) {
        super(taskService, listener);
        this.controlService = controlService;
        this.repositoryService = repositoryService;
    }

    public void executeControlTask(String hostId, String controlTask, Map<String, String> parameters) {
        verifyHost(hostId);
        Control control = controlService.prepare(controlTask, hostId);
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            control.getParameters().setProperty(parameter.getKey(), parameter.getValue());
        }
        String taskId = controlService.createTask(control);
        try {
            executeTask(taskId, false, false);
        } catch (RuntimeException e) {
            throw new DeployitPluginException(e.getMessage());
        }
    }

    private void verifyHost(String hostId) {
        try {
            ConfigurationItem repoPackage = repositoryService.read(hostId);
            listener.debug(String.format("Found CI '%s' as '%s' .", repoPackage, repoPackage.getType()));

            Type deployedApplicationType = Type.valueOf(DeployitDescriptorRegistry.UDM_CONTAINER);
            if (!repoPackage.getType().instanceOf(deployedApplicationType)) {
                String errorMsg = String.format("'%s' of type '%s' is not a container.", hostId, repoPackage.getType());
                throw new DeployitPluginException(errorMsg);
            }
        }
        catch (Throwable t) {
            String errorMsg = String.format("'%s' not found in repository.", hostId);
            throw new DeployitPluginException(errorMsg, t);
        }
    }

}
