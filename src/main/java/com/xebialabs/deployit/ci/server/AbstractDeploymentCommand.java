package com.xebialabs.deployit.ci.server;

import com.google.common.base.Throwables;
import com.xebialabs.deployit.ci.DeployitPluginException;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.execution.StepExecutionState;
import com.xebialabs.deployit.engine.api.execution.StepState;
import com.xebialabs.deployit.engine.api.execution.TaskExecutionState;
import com.xebialabs.deployit.engine.api.execution.TaskState;
import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;

public abstract class AbstractDeploymentCommand {

    protected TaskService taskService;
    protected JenkinsDeploymentListener listener;

    protected AbstractDeploymentCommand(TaskService taskService, JenkinsDeploymentListener listener) {
        this.taskService = taskService;
        this.listener = listener;
    }

    protected boolean executeTask(String taskId, boolean skipMode, boolean testMode) {

        if (skipMode) {
            listener.info("skip mode, skip all the steps");
            taskService.skip(taskId, range(taskService.getTask(taskId).getNrSteps() + 1));
        }

        checkTaskState(taskId);
        if (testMode) {
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
            String msg = format("Error when executing task %s", taskId);
            throw new DeployitPluginException(msg);
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
