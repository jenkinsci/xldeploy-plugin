package com.xebialabs.deployit.ci.server;

import java.nio.charset.Charset;

import org.junit.Test;

import com.xebialabs.deployit.ci.JenkinsDeploymentOptions;
import com.xebialabs.deployit.ci.VersionKind;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.DeploymentService;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.dto.Deployment;
import com.xebialabs.deployit.engine.api.execution.TaskState;

import hudson.model.StreamBuildListener;

import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeployCommandTest {

    @Test
    public void shouldRetryTaskStatusCheckFiveTimesAfterExceptionOccurs() {
        DeploymentService deploymentService = mock(DeploymentService.class);
        TaskService taskService = mock(TaskService.class);
        JenkinsDeploymentOptions jenkinsOptions = new JenkinsDeploymentOptions("test", VersionKind.Packaged, false, false, false, false);
        JenkinsDeploymentListener jenkinsDeploymentListener = new JenkinsDeploymentListener(new StreamBuildListener(System.out, Charset.defaultCharset()), true);
        DeployCommand deployCommand = new DeployCommand(deploymentService, taskService, jenkinsOptions, jenkinsDeploymentListener);

        Deployment deployment = new Deployment();
        when(deploymentService.prepareInitial("pkg", "test")).thenReturn(deployment);
        when(deploymentService.validate(deployment)).thenReturn(deployment);
        when(deploymentService.createTask(deployment)).thenReturn("123");

        TaskState taskState = mock(TaskState.class);

        when(taskService.getTask("123"))
                .thenReturn(taskState)
                .thenThrow(new RuntimeException("Try 1"))
                .thenThrow(new RuntimeException("Try 2"))
                .thenThrow(new RuntimeException("Try 3"))
                .thenThrow(new RuntimeException("Try 4"))
                .thenThrow(new RuntimeException("Try 5"))
                .thenThrow(new MyTestValidationException("Expect this to be rethrown"));

        try {
            deployCommand.deploy("pkg", "test");
            fail("Expected exception after 5 failed attempts.");
        } catch  (MyTestValidationException e ) {
            //success
        }
    }

    public static class MyTestValidationException extends RuntimeException {
        public MyTestValidationException(final String message) {
            super(message);
        }
    }
}
