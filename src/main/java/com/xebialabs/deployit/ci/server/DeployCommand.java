/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 *
 *
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.deployit.ci.server;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Throwables;

import com.xebialabs.deployit.ci.DeployitPluginException;
import com.xebialabs.deployit.ci.JenkinsDeploymentOptions;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.DeploymentService;
import com.xebialabs.deployit.engine.api.RepositoryService;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.dto.Deployment;
import com.xebialabs.deployit.engine.api.dto.ValidatedConfigurationItem;
import com.xebialabs.deployit.engine.api.execution.StepExecutionState;
import com.xebialabs.deployit.engine.api.execution.StepState;
import com.xebialabs.deployit.engine.api.execution.TaskExecutionState;
import com.xebialabs.deployit.engine.api.execution.TaskState;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.validation.ValidationMessage;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;

public class DeployCommand {

    private DeploymentService deploymentService;
    private TaskService taskService;
    private JenkinsDeploymentOptions deploymentOptions;
    private JenkinsDeploymentListener listener;
    private RepositoryService repositoryService;

    DeployCommand(DeploymentService deploymentService, TaskService taskService, RepositoryService repositoryService, JenkinsDeploymentOptions deploymentOptions, JenkinsDeploymentListener listener) {
        this.deploymentService = deploymentService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.deploymentOptions = deploymentOptions;
        this.listener = listener;
    }

    private void verifyPackageExistInRemoteRepository(String deploymentPackage) {
        boolean found = false;
        Type foundType = null;
        try {
            ConfigurationItem repoPackage = repositoryService.read(deploymentPackage);
            foundType = repoPackage.getType();
            listener.debug(String.format("Found CI '%s' as '%s' .", repoPackage, foundType));
        }
        catch (Throwable t) {
            String errorMsg = String.format("'%s' not found in repository.", deploymentPackage);
            throw new DeployitPluginException(errorMsg, t);
        }

        Type deploymentPackageType = Type.valueOf("udm.DeploymentPackage");
        if (!deploymentPackageType.equals(foundType)) {
            String errorMsg = String.format("'%s' is of type '%s' instead 'udm.DeploymentPackage'. Please verify that 'Version' is specified in jenkins configuration.", deploymentPackage, foundType);
            throw new DeployitPluginException(errorMsg);
        }

    }

    public void deploy(String deploymentPackage, String environment) {
        listener.debug(deploymentOptions.toString());

        verifyPackageExistInRemoteRepository(deploymentPackage);
        boolean initialDeployment = !deploymentService.isDeployed(DeployitServerFactory.getParentId(deploymentPackage), environment);

        Deployment deployment;
        if (initialDeployment) {
            listener.info("initial Deployment");
            deployment = deploymentService.prepareInitial(deploymentPackage, environment);
        } else {
            listener.info("upgrade Deployment");
            String deployedApplicationId = environment + "/" + DeployitServerFactory.getNameFromId(DeployitServerFactory.getParentId(deploymentPackage));
            deployment = deploymentService.prepareUpdate(deploymentPackage, deployedApplicationId);
        }

        if (deploymentOptions.generateDeployedOnUpgrade) {
            listener.debug("prepareAutoDeployeds");
            deployment = deploymentService.prepareAutoDeployeds(deployment);
        }

        listener.debug(" dump Deployeds");
        for (ConfigurationItem itemDto : deployment.getDeployeds()) {
            listener.debug(" - " + itemDto);
        }

        try {
            listener.debug("validate");
            deployment = deploymentService.validate(deployment);
        } catch (RuntimeException e) {
            listener.error(" RuntimeException: " + e.getMessage());
            if (!e.getMessage().contains("The task did not deliver any steps")) {
                throw new DeployitPluginException(e.getMessage(), e);
            }
            return;
        }

        int validationMessagesFound = 0;
        for (ConfigurationItem configurationItem : deployment.getDeployeds()) {
            if (!(configurationItem instanceof ValidatedConfigurationItem)) {
                continue;
            }
            for (ValidationMessage msg : ((ValidatedConfigurationItem) configurationItem).getValidations()) {
                listener.error(String.format("Validation error found on item '%s' of type '%s' on field '%s': %s, %s", configurationItem.getId(), configurationItem.getType(), msg.getCiId(), msg.getMessage(), configurationItem));
                listener.error(String.format(" %s", configurationItem));
                validationMessagesFound++;
            }
        }

        if (validationMessagesFound > 0) {
            throw new DeployitPluginException(String.format("Validation errors (%d) have been found. For more information previously reported ERROR messages.", validationMessagesFound));
        }

        listener.debug("deploy");

        String taskId = deploymentService.createTask(deployment);

        try {
            executeTask(taskId);
        } catch (RuntimeException e) {
            try {
                if (deploymentOptions.rollbackOnError) {
                    // perform a rollback
                    listener.error("Deployment failed, performing a rollback");
                    executeTask(deploymentService.rollback(taskId));
                }
            } finally {
                throw new DeployitPluginException(e.getMessage(), e);
            }
        }
    }

    private boolean executeTask(String taskId) {

        if (deploymentOptions.skipMode) {
            listener.info("skip mode, skip all the steps");
            taskService.skip(taskId, range(taskService.getTask(taskId).getNrSteps() + 1));
        }

        checkTaskState(taskId);
        if (deploymentOptions.testMode) {
            listener.info("test mode, cancel task " + taskId);
            taskService.cancel(taskId);
            return false;
        }

        try {
            listener.info("Start deployment task " + taskId);
            startTaskAndWait(taskId);
            checkTaskState(taskId);
            taskService.archive(taskId);
            return true;
        } catch (RuntimeException e) {
            String msg = format("Error when executing task %s: %s", taskId, e.getMessage());
            listener.error(msg);
            throw new DeployitPluginException(msg, e);
        }

    }

    private List<Integer> range(int end) {
        List<Integer> result = newArrayList();
        for (int i = 1; i < end; i++) {
            result.add(i);
        }
        return result;
    }

    private void checkTaskState(String taskId) {
        TaskState taskState = taskService.getTask(taskId);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        listener.info(format("%s Description	%s", taskId, taskState.getDescription()));
        listener.info(format("%s State      	%s %d/%d", taskId, taskState.getState(), taskState.getCurrentStepNr(), taskState.getNrSteps()));
        if (taskState.getStartDate() != null) {
            final GregorianCalendar startDate = taskState.getStartDate().toGregorianCalendar();
            listener.info(format("%s Start      %s", taskId, sdf.format(startDate.getTime())));
        }

        if (taskState.getCompletionDate() != null) {
            final GregorianCalendar completionDate = taskState.getCompletionDate().toGregorianCalendar();
            listener.info(format("%s Completion %s", taskId, sdf.format(completionDate.getTime())));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= taskState.getNrSteps(); i++) {
            final StepState stepInfo = taskService.getStep(taskId, i, null);
            final String description = stepInfo.getDescription();
            final String log = stepInfo.getLog();
            String stepInfoMessage;
            if (StringUtils.isEmpty(log) || description.equals(log)) {
                stepInfoMessage = format("%s step #%d %s\t%s", taskId, i, stepInfo.getState(), description);
            } else {
                stepInfoMessage = format("%s step #%d %s\t%s\n%s", taskId, i, stepInfo.getState(), description, log);
            }

            listener.info(stepInfoMessage);
            if (StepExecutionState.FAILED.equals(stepInfo.getState()))
                sb.append(stepInfoMessage);
        }

        if (taskState.getState().isExecutionHalted())
            throw new DeployitPluginException(format("Errors when executing task %s: %s", taskId, sb));
    }

    private void startTaskAndWait(String taskId) {
        taskService.start(taskId);
        // Wait until done/failed
        boolean done = false;
        TaskState ti;

        int retryCount = 1;
        while (!done) {
            try {
                ti = taskService.getTask(taskId);
                TaskExecutionState state = ti.getState();
                listener.debug("Task state: " + state.toString());
                done = state.isPassiveAfterExecuting();
                retryCount = 1;
            } catch (Exception e) {
                if (retryCount == 6) {      //fail after 5 consecutive errors.
                    Throwables.propagate(e);
                } else {
                    listener.info("Failed to get task status. Error message: " + Throwables.getRootCause(e).getMessage());
                    listener.info("Will attempt retry " + retryCount + " of 5 in one second.");
                    retryCount++;
                }
            }

            try {
                listener.debug("Waiting for task to be done...");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}