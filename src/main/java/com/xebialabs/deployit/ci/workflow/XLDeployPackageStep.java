package com.xebialabs.deployit.ci.workflow;

import com.google.inject.Inject;
import com.sun.istack.NotNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class XLDeployPackageStep extends AbstractStepImpl {

    public String manifestPath = null;
    public String artifactPath = null;
    public String packageName = null;
    public String packageVersion = null;

    @DataBoundConstructor
    public XLDeployPackageStep(String manifestPath, String artifactPath, String packageVersion, String packageName) {
        this.manifestPath = manifestPath;
        this.artifactPath = artifactPath;
        this.packageVersion = packageVersion;
        this.packageName = packageName;
    }

    @DataBoundSetter
    public void setManifestPath(@NotNull String manifestPath) {
        this.manifestPath = Util.fixEmptyAndTrim(manifestPath);
    }

    @DataBoundSetter
    public void setArtifactPath(@NotNull String artifactPath) {
        this.artifactPath = artifactPath;
    }

    @DataBoundSetter
    public void setPackageVersion(@NotNull String packageVersion) {
        this.packageVersion = packageVersion;
    }

    @DataBoundSetter
    public void setPackageName(@NotNull String packageName) {
        this.packageName = packageName;
    }

    @Override
    public XLDeployPackageStepDescriptor getDescriptor() {
        return (XLDeployPackageStepDescriptor) super.getDescriptor();
    }

    @Extension
    public static final class XLDeployPackageStepDescriptor extends AbstractStepDescriptorImpl {

        public XLDeployPackageStepDescriptor() {
            super(XLDeployPackageExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "xlDeployPackage";
        }

        @Override
        public String getDisplayName() {
            return "Create a deployment package";
        }

    }

    public static final class XLDeployPackageExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        @Inject
        private transient XLDeployPackageStep step;

        @StepContextParameter
        private transient EnvVars envVars;

        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected Void run() throws Exception {
            String packagePath = outputFilePath();
            createDARPackage(getFullPath(step.manifestPath), getFullPath(step.artifactPath), packagePath);
            listener.getLogger().println("XL Deploy package created : " + packagePath);
            return null;
        }

        private String outputFilePath() {
            return new StringBuilder(envVars.get("WORKSPACE")).append(File.separator).append(step.packageName).append("-").append(step.packageVersion).append(".dar").toString();
        }

        private String getFullPath(String filePath) {
            return new StringBuilder(envVars.get("WORKSPACE")).append(File.separator).append(filePath).toString();
        }

        private void createDARPackage(String manifestPath, String artifactPath, String packagePath) throws IOException {
            FileOutputStream fileOutputStream = new FileOutputStream(packagePath);
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
            addFileToPackage(new File(artifactPath), zipOutputStream);
            addFileToPackage(new File(manifestPath), zipOutputStream);
            zipOutputStream.closeEntry();
            zipOutputStream.close();
            fileOutputStream.close();
        }

        private void addFileToPackage(File inputFile, ZipOutputStream zipOutputStream) throws IOException {
            ZipEntry zipEntry = new ZipEntry(inputFile.getName());
            zipOutputStream.putNextEntry(zipEntry);
            FileInputStream fileInputStream = new FileInputStream(inputFile);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buf)) > 0) {
                zipOutputStream.write(buf, 0, bytesRead);
            }
        }

    }

}
