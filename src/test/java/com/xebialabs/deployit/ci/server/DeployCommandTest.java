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

import java.nio.charset.Charset;

import org.junit.Test;

import com.xebialabs.deployit.ci.JenkinsDeploymentOptions;
import com.xebialabs.deployit.ci.VersionKind;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.engine.api.DeploymentService;
import com.xebialabs.deployit.engine.api.RepositoryService;
import com.xebialabs.deployit.engine.api.TaskService;
import com.xebialabs.deployit.engine.api.dto.Deployment;
import com.xebialabs.deployit.engine.api.execution.TaskExecutionState;
import com.xebialabs.deployit.engine.api.execution.TaskState;

import hudson.model.StreamBuildListener;
import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeployCommandTest {

    public void shouldRetryTaskStatusCheckFiveTimesAfterExceptionOccurs() {
        DeploymentService deploymentService = mock(DeploymentService.class);
        TaskService taskService = mock(TaskService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        JenkinsDeploymentOptions jenkinsOptions = new JenkinsDeploymentOptions("test", VersionKind.Packaged, false, false, false, false);
        JenkinsDeploymentListener jenkinsDeploymentListener = new JenkinsDeploymentListener(new StreamBuildListener(System.out, Charset.defaultCharset()), true);
        DeployCommand deployCommand = new DeployCommand(deploymentService, taskService, repositoryService, jenkinsOptions, jenkinsDeploymentListener);

        Deployment deployment = new Deployment();
        when(deploymentService.prepareInitial("pkg", "test")).thenReturn(deployment);
        when(deploymentService.validate(deployment)).thenReturn(deployment);
        when(deploymentService.createTask(deployment)).thenReturn("123");

        TaskState taskState = mock(TaskState.class);
        when(taskState.getState()).thenReturn(TaskExecutionState.DONE);

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
