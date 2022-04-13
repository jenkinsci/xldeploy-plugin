package com.xebialabs.deployit.ci.workflow;

import com.google.inject.Inject;
import com.xebialabs.deployit.ci.DeployitNotifier;
import com.xebialabs.deployit.ci.JenkinsDeploymentOptions;
import com.xebialabs.deployit.ci.RepositoryUtils;
import com.xebialabs.deployit.ci.VersionKind;
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
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;


public class XLDeployDeployStep extends AbstractStepImpl {
    public final String serverCredentials;
    public final String packageId;
    public final String environmentId;
    public String overrideCredentialId;
    public Boolean rollbackOnError;
    public Boolean failOnArchiveFailure;

    @DataBoundConstructor
    public XLDeployDeployStep(String serverCredentials, String packageId,
                              String environmentId) {
        this.serverCredentials = serverCredentials;
        this.environmentId = environmentId;
        this.packageId = packageId;
        this.rollbackOnError = true;
        this.failOnArchiveFailure = true;
    }

    @DataBoundSetter
    public void setOverrideCredentialId(String overrideCredentialId) {
        this.overrideCredentialId = overrideCredentialId;
    }

    @DataBoundSetter
    public void setRollbackOnError(Boolean rollbackOnError) {
        if (rollbackOnError == null) {
            this.rollbackOnError = true;
        } else {
            this.rollbackOnError = rollbackOnError;
        }
    }

    @DataBoundSetter
    public void setFailOnArchiveFailure(Boolean failOnArchiveFailure) {
        if (failOnArchiveFailure == null) {
            this.failOnArchiveFailure = true;
        } else {
            this.failOnArchiveFailure = failOnArchiveFailure;
        }
    }

    @Extension
    public static final class XLDeployDeployStepDescriptor extends AbstractStepDescriptorImpl {

        private DeployitNotifier.DeployitDescriptor deployitDescriptor;

        public XLDeployDeployStepDescriptor() {
            super(XLDeployPublishExecution.class);
            deployitDescriptor = new DeployitNotifier.DeployitDescriptor();
        }

        @Override
        public String getFunctionName() {
            return "xldDeploy";
        }

        @Override
        public String getDisplayName() {
            return "Deploy a package to a environment";
        }

        public ListBoxModel doFillServerCredentialsItems() {
            return getDeployitDescriptor().doFillCredentialItems();
        }

        private DeployitNotifier.DeployitDescriptor getDeployitDescriptor() {
            deployitDescriptor.load();
            return deployitDescriptor;
        }

    }

    public static final class XLDeployPublishExecution extends AbstractSynchronousNonBlockingStepExecution<Void>
    {
        @Inject
        private transient XLDeployDeployStep step;

        @StepContextParameter
        private transient EnvVars envVars;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Run<?,?> run;

        @Override
        protected Void run() throws Exception {
            String resolvedEnvironmentId = envVars.expand(step.environmentId);
            String resolvedPackageId = envVars.expand(step.packageId);
            String resolvedRollbackOnError = envVars.expand(Boolean.toString(step.rollbackOnError));
            String resolvedFailOnArchiveFailure = envVars.expand(Boolean.toString(step.failOnArchiveFailure));
            JenkinsDeploymentListener deploymentListener = new JenkinsDeploymentListener(listener, false);
            JenkinsDeploymentOptions deploymentOptions = new JenkinsDeploymentOptions(resolvedEnvironmentId, VersionKind.Other, true, false , false, Boolean.parseBoolean(resolvedRollbackOnError), Boolean.parseBoolean(resolvedFailOnArchiveFailure));
            Job<?,?> job = this.run.getParent();
            DeployitServer deployitServer = RepositoryUtils.getDeployitServerFromCredentialsId(
                    step.serverCredentials, step.overrideCredentialId, job);
            deployitServer.deploy(resolvedPackageId,resolvedEnvironmentId,deploymentOptions,deploymentListener);
            return null;
        }
    }
}
