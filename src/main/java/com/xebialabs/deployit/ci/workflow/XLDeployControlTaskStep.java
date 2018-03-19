package com.xebialabs.deployit.ci.workflow;

import com.google.inject.Inject;
import com.xebialabs.deployit.ci.DeployitNotifier;
import com.xebialabs.deployit.ci.RepositoryUtils;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.HashMap;
import java.util.Map;


public class XLDeployControlTaskStep extends AbstractStepImpl {

    public final String serverCredentials;
    public final String hostId;
    public final String controlTask;
    public final Map<String, String> parameters;

    @DataBoundConstructor
    public XLDeployControlTaskStep(String serverCredentials, String hostId, String controlTask, Map<String, String> parameters) {
        this.serverCredentials = serverCredentials;
        this.hostId = hostId;
        this.controlTask = controlTask;
        this.parameters = parameters;
    }

    @Extension
    public static final class XLDeployControlTaskStepDescriptor extends AbstractStepDescriptorImpl {

        private DeployitNotifier.DeployitDescriptor deployitDescriptor;

        public XLDeployControlTaskStepDescriptor() {
            super(XLDeployControlTaskExecution.class);
            deployitDescriptor = new DeployitNotifier.DeployitDescriptor();
        }

        @Override
        public String getFunctionName() {
            return "xldControlTask";
        }

        @Override
        public String getDisplayName() {
            return "Execute a control task";
        }

        public ListBoxModel doFillServerCredentialsItems() {
            return getDeployitDescriptor().doFillCredentialItems();
        }

        private DeployitNotifier.DeployitDescriptor getDeployitDescriptor() {
            deployitDescriptor.load();
            return deployitDescriptor;
        }

    }

    public static final class XLDeployControlTaskExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject
        private transient XLDeployControlTaskStep step;

        @StepContextParameter
        private transient EnvVars envVars;

        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {
            String resolvedHostId = envVars.expand(step.hostId);
            Map<String, String> resolvedParameters = new HashMap<>();
            if (step.parameters != null) {
                for (Map.Entry<String, String> parameter : step.parameters.entrySet()) {
                    resolvedParameters.put(envVars.expand(parameter.getKey()), envVars.expand(parameter.getValue()));
                }
            }
            JenkinsDeploymentListener deploymentListener = new JenkinsDeploymentListener(listener, false);
            DeployitServer deployitServer = RepositoryUtils.getDeployitServer(step.serverCredentials, null);
            deployitServer.executeControlTask(resolvedHostId, step.controlTask, resolvedParameters, deploymentListener);
            return null;
        }

    }
}
