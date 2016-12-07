package com.xebialabs.deployit.ci.workflow;

import com.google.inject.Inject;
import com.sun.istack.NotNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.lang.StringUtils.isNotBlank;

public class XLDeployPackageStep extends AbstractStepImpl {

    public List<Resource> artifacts = Collections.emptyList();
    public Resource manifest = null;
    public String packageName = null;
    public String packageVersion = null;
    public String manifestPath = null;
    public String manifestUsername = null;
    public String manifestPassword = null;

    @DataBoundConstructor
    public XLDeployPackageStep(List<Resource> artifacts, String manifestPath, String manifestUsername, String manifestPassword, String packageVersion, String packageName) {
        this.artifacts = artifacts;
        this.manifestPath = manifestPath;
        this.manifestUsername = manifestUsername;
        this.manifestPassword = manifestPassword;
        this.manifest = new Resource(manifestPath, manifestUsername, manifestPassword);
        this.packageVersion = packageVersion;
        this.packageName = packageName;
    }

    @DataBoundSetter
    public void setManifestPath(@NotNull String manifestPath) {
        this.manifestPath = manifestPath;
    }

    @DataBoundSetter
    public void setManifestUsername(String manifestUsername) {
        this.manifestUsername = manifestUsername;
    }

    @DataBoundSetter
    public void setManifestPassword(String manifestPassword) {
        this.manifestPassword = manifestPassword;
    }

    @DataBoundSetter
    public void setArtifacts(@NotNull List<Resource> artifacts) {
        this.artifacts = artifacts;
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
            FileOutputStream fileOutputStream = new FileOutputStream(packagePath);
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

            addResourceToPackage(step.manifest, zipOutputStream);
            for (Resource artifact : step.artifacts) {
                addResourceToPackage(artifact, zipOutputStream);
            }
            zipOutputStream.closeEntry();
            zipOutputStream.close();
            fileOutputStream.close();

            listener.getLogger().println("XL Deploy package created : " + packagePath);
            return null;
        }

        private void addResourceToPackage(Resource resource, ZipOutputStream zipOutputStream) throws IOException {

            ZipEntry zipEntry;
            InputStream inputStream;

            if (resource.isURLResource()) {
                URL url = new URL(resource.path);
                URLConnection urlConnection = url.openConnection();
                setAuthData(urlConnection, resource);
                inputStream = urlConnection.getInputStream();
                zipEntry = new ZipEntry(FilenameUtils.getName(url.getPath()));
            } else {
                File resourceFile = new File(getGeneratedFilePath(resource.path));
                zipEntry = new ZipEntry(resourceFile.getName());
                inputStream = new FileInputStream(resourceFile);
            }

            zipOutputStream.putNextEntry(zipEntry);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buf)) > 0) {
                zipOutputStream.write(buf, 0, bytesRead);
            }
        }

        private void setAuthData(URLConnection urlConnection, Resource resource) {
            if (isNotBlank(resource.username) && isNotBlank(resource.password)) {
                urlConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeBase64String((resource.username + ":" + resource.password).getBytes()));
            }
        }

        private String getGeneratedFilePath(String filePath) {
            return getWorkspace().append(filePath).toString();
        }

        private String outputFilePath() {
            return getWorkspace().append(step.packageName).append("-").append(step.packageVersion).append(".dar").toString();
        }

        private StringBuilder getWorkspace() {
            return new StringBuilder(envVars.get("WORKSPACE")).append(File.separator);
        }

    }

}
