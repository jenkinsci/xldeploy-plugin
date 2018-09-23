package com.xebialabs.deployit.ci.workflow;

import com.google.inject.Inject;
import com.xebialabs.deployit.ci.ArtifactView;
import com.xebialabs.deployit.ci.RemoteAwareLocation;
import com.xebialabs.deployit.ci.RepositoryUtils;
import com.xebialabs.deployit.ci.DeployitNotifier.DeployitDescriptor;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;

public class XLDeployPublishStep extends AbstractStepImpl {

    public final String serverCredentials;
    public String overrideCredentialId;
    public final String darPath;

    @DataBoundConstructor
    public XLDeployPublishStep(String darPath, String serverCredentials) {
        this.darPath = darPath;
        this.serverCredentials = serverCredentials;
    }

    @DataBoundSetter
    public void setOverrideCredentialId(String overrideCredentialId) {
        this.overrideCredentialId = overrideCredentialId;
    }

    @Extension
    public static final class XLDeployPublishStepDescriptor extends AbstractStepDescriptorImpl {

        private DeployitDescriptor deployitDescriptor;

        public XLDeployPublishStepDescriptor() {
            super(XLDeployPublishExecution.class);
            deployitDescriptor = new DeployitDescriptor();
        }

        @Override
        public String getFunctionName() {
            return "xldPublishPackage";
        }

        @Override
        public String getDisplayName() {
            return "Publish a deployment package to XLDeploy";
        }

        public ListBoxModel doFillServerCredentialsItems() {
            return getDeployitDescriptor().doFillCredentialItems();
        }

        private DeployitDescriptor getDeployitDescriptor() {
            deployitDescriptor.load();
            return deployitDescriptor;
        }

    }

    public static final class XLDeployPublishExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject
        private transient XLDeployPublishStep step;

        @StepContextParameter
        private transient EnvVars envVars;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient Run<?,?> run;

        @Override
        protected Void run() throws Exception {
            JenkinsDeploymentListener deploymentListener = new JenkinsDeploymentListener(listener, false);
            final String path = ArtifactView.findFilePathFromPattern(envVars.expand(step.darPath), ws, deploymentListener);
            RemoteAwareLocation location = getRemoteAwareLocation(path);
            try {
                Job<?,?> job = this.run.getParent();
                DeployitServer deployitServer = RepositoryUtils.getDeployitServerFromCredentialsId(step.serverCredentials, step.overrideCredentialId, job);

                deployitServer.importPackage(location.getDarFileLocation(ws, deploymentListener, envVars));
            } finally {
                location.cleanup();
            }

            return null;
        }

        private RemoteAwareLocation getRemoteAwareLocation(final String path) {
            return new RemoteAwareLocation() {
                @Override
                public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener, EnvVars envVars) {
                    return getRemoteAwareLocation(ws, path);
                }
            };
        }
    }
}
