package com.xebialabs.deployit.ci.server;

import com.xebialabs.deployit.ci.DeployitPluginException;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.DeploymentService;
import com.xebialabs.deployit.engine.api.RepositoryService;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.dto.Deployment;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;

public class UndeployCommand extends AbstractDeploymentCommand {

    private DeploymentService deploymentService;
    private RepositoryService repositoryService;

    public UndeployCommand(TaskService taskService, JenkinsDeploymentListener listener, DeploymentService deploymentService, RepositoryService repositoryService) {
        super(taskService, listener);
        this.deploymentService = deploymentService;
        this.repositoryService = repositoryService;
    }

    public void undeploy(String deployedApplication) {
        verifyDeployedApplication(deployedApplication);
        Deployment deployment = deploymentService.prepareUndeploy(deployedApplication);
        try {
            deployment = deploymentService.validate(deployment);
        } catch (RuntimeException e) {
            listener.error(" RuntimeException: " + e.getMessage());
            if (!e.getMessage().contains("The task did not deliver any steps")) {
                throw new DeployitPluginException(e.getMessage(), e);
            }
            return;
        }

        String taskId = deploymentService.createTask(deployment);

        try {
            executeTask(taskId, false, false);
        } catch (RuntimeException e) {
            throw new DeployitPluginException(e.getMessage());
        }
    }

    private void verifyDeployedApplication(String deployedApplication) {
        try {
            ConfigurationItem repoPackage = repositoryService.read(deployedApplication);
            listener.debug(String.format("Found CI '%s' as '%s' .", repoPackage, repoPackage.getType()));

            Type deployedApplicationType = Type.valueOf(DeployitDescriptorRegistry.UDM_DEPLOYED_APPLICATION);
            if (!repoPackage.getType().instanceOf(deployedApplicationType)) {
                String errorMsg = String.format("'%s' of type '%s' is not a deployed application.", deployedApplication, repoPackage.getType());
                throw new DeployitPluginException(errorMsg);
            }
        }
        catch (Throwable t) {
            String errorMsg = String.format("'%s' not found in repository.", deployedApplication);
            throw new DeployitPluginException(errorMsg, t);
        }
    }

}
