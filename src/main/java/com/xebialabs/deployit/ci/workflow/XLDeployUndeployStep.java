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


public class XLDeployUndeployStep extends AbstractStepImpl {

    public final String serverCredentials;
    public final String deployedApplicationId;

    @DataBoundConstructor
    public XLDeployUndeployStep(String serverCredentials, String deployedApplicationId) {
        this.serverCredentials = serverCredentials;
        this.deployedApplicationId = deployedApplicationId;
    }

    @Extension
    public static final class XLDeployDeployStepDescriptor extends AbstractStepDescriptorImpl {

        private DeployitNotifier.DeployitDescriptor deployitDescriptor;

        public XLDeployDeployStepDescriptor() {
            super(XLDeployUndeployExecution.class);
            deployitDescriptor = new DeployitNotifier.DeployitDescriptor();
        }

        @Override
        public String getFunctionName() {
            return "xldUndeploy";
        }

        @Override
        public String getDisplayName() {
            return "Undeploy a deployed application";
        }

        public ListBoxModel doFillServerCredentialsItems() {
            return getDeployitDescriptor().doFillCredentialItems();
        }

        private DeployitNotifier.DeployitDescriptor getDeployitDescriptor() {
            deployitDescriptor.load();
            return deployitDescriptor;
        }

    }

    public static final class XLDeployUndeployExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject
        private transient XLDeployUndeployStep step;

        @StepContextParameter
        private transient EnvVars envVars;

        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {
            String resolvedDeployedApplicationId = envVars.expand(step.deployedApplicationId);
            JenkinsDeploymentListener deploymentListener = new JenkinsDeploymentListener(listener, false);
            DeployitServer deployitServer = RepositoryUtils.getDeployitServer(step.serverCredentials, null);
            deployitServer.undeploy(resolvedDeployedApplicationId, deploymentListener);
            return null;
        }

    }
}
