package com.xebialabs.deployit.ci.server;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;

import com.google.common.base.Throwables;

import com.xebialabs.deployit.ci.JenkinsDeploymentOptions;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.DeploymentService;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.dto.Deployment;
import com.xebialabs.deployit.engine.api.dto.ValidatedConfigurationItem;
import com.xebialabs.deployit.engine.api.execution.StepExecutionState;
import com.xebialabs.deployit.engine.api.execution.StepState;
import com.xebialabs.deployit.engine.api.execution.TaskExecutionState;
import com.xebialabs.deployit.engine.api.execution.TaskState;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.validation.ValidationMessage;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

public class DeployCommand {

    private DeploymentService deploymentService;
    private TaskService taskService;
    private JenkinsDeploymentOptions deploymentOptions;
    private JenkinsDeploymentListener listener;

    DeployCommand(DeploymentService deploymentService, TaskService taskService, JenkinsDeploymentOptions deploymentOptions, JenkinsDeploymentListener listener) {
        this.deploymentService = deploymentService;
        this.taskService = taskService;
        this.deploymentOptions = deploymentOptions;
        this.listener = listener;
    }

    public void deploy(String deploymentPackage, String environment) {
        listener.debug(deploymentOptions.toString());
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
                throw e;
            }
            return;
        }

        int validationMessagesFound = 0;
        for (ConfigurationItem configurationItem : deployment.getDeployeds()) {
            if (!(configurationItem instanceof ValidatedConfigurationItem)) {
                continue;
            }
            for (ValidationMessage msg : ((ValidatedConfigurationItem) configurationItem).getValidations()) {
                listener.error(String.format("Validation error found on '%s' on field '%s': %s, %s", configurationItem.getId(), msg.getCiId(), msg.getMessage(), configurationItem));
                listener.error(String.format(" %s", configurationItem));
                validationMessagesFound++;
            }
        }

        if (validationMessagesFound > 0) {
            throw new IllegalStateException(String.format("Validation errors (%d) have been found", validationMessagesFound));
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
                throw e;
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
            listener.error(format("Error when executing task %s: %s", taskId, e.getMessage()));
            throw e;
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

        if (TaskExecutionState.STOPPED.equals(taskState.getState()))
            throw new IllegalStateException(format("Errors when executing task %s: %s", taskId, sb));
    }

    private void startTaskAndWait(String taskId) {
        taskService.start(taskId);
        // Wait until done/failed
        boolean done = false;
        TaskState ti;

        Set<TaskExecutionState> doneStates = newHashSet(TaskExecutionState.DONE, TaskExecutionState.EXECUTED,
                TaskExecutionState.STOPPED, TaskExecutionState.CANCELLED);

        int retryCount = 1;
        while (!done) {
            try {
                ti = taskService.getTask(taskId);
                TaskExecutionState state = ti.getState();
                listener.debug("Task state: " + state.toString());
                done = doneStates.contains(state);
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