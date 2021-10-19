package com.xebialabs.deployit.ci;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import com.xebialabs.deployit.ci.dar.RemotePackaging;
import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.server.DeployitServerFactory;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;

/**
 * Package visible performer used from DeployitNotifier that does the real work.
 *
 * @author ilx
 */
class DeployitPerformer {
    private AbstractBuild<?, ?> build;
    private JenkinsDeploymentListener deploymentListener;
    private BuildListener buildListener;
    private DeployitServer deployitServer;
    private DeployitPerformerParameters deploymentParameters;

    DeployitPerformer(final AbstractBuild<?, ?> build, BuildListener buildListener, DeployitServer deployitServer, DeployitPerformerParameters deploymentParameters) {
        this.build = build;
        this.buildListener = buildListener;
        this.deployitServer = deployitServer;
        this.deploymentParameters = deploymentParameters;
        this.deploymentListener = new JenkinsDeploymentListener(buildListener, deploymentParameters.verbose);
    }

    public boolean doPerform() throws InterruptedException, IOException {
        final EnvVars envVars = build.getEnvironment(buildListener);
        String resolvedApplication = envVars.expand(deploymentParameters.application);

        String applicationName = DeployitServerFactory.getNameFromId(resolvedApplication);
        List<String> qualifiedAppIds = deployitServer.search(DeployitDescriptorRegistry.UDM_APPLICATION, applicationName);
        if (qualifiedAppIds.size() == 1) {
            resolvedApplication = qualifiedAppIds.get(0);
        }
        String resolvedVersion = envVars.expand(deploymentParameters.version);

        //Package
        if (deploymentParameters.packageOptions != null) {
            deploymentListener.info(Messages.DeployitNotifier_package(resolvedApplication, resolvedVersion));
            verifyResolvedVersion(resolvedVersion);
            verifyResolvedApplication(resolvedApplication);

            final FilePath workspace = build.getWorkspace();
            if (deploymentParameters.deploymentOptions != null && deploymentParameters.deploymentOptions.versionKind == VersionKind.Packaged) {
                deploymentParameters.deploymentOptions.setVersion(resolvedVersion);
            }

            DeployitDescriptorRegistry descriptorRegistry = deployitServer.getDescriptorRegistry();
            DeploymentPackage deploymentPackage = deploymentParameters.packageOptions.toDeploymentPackage(resolvedApplication, resolvedVersion, deploymentParameters.packageProperties, descriptorRegistry, workspace, envVars, deploymentListener);
            final File targetDir = new File(workspace.absolutize().getRemote(), "deployitpackage");

            String packagedPath = workspace.getChannel().call(
                    new RemotePackaging()
                            .withTargetDir(targetDir)
                            .forDeploymentPackage(deploymentPackage)
                            .usingConfig(deployitServer.getBooterConfig())
                            .usingDescriptors(Lists.newArrayList(descriptorRegistry.getDescriptors()))
                            .withRegistryVersion(deployitServer.getRegistryVersion())
            );

            if (deploymentParameters.importOptions != null && packagedPath != null) {
                deploymentParameters.importOptions.setGeneratedDarLocation(packagedPath);
            }
            deploymentListener.info(Messages.DeployitNotifier_packaged(resolvedApplication, packagedPath));
        }

        //Import

        String importedVersion = "";
        if (deploymentParameters.importOptions != null) {
            String resolvedDarFileLocation = "";
            try {
                final String darFileLocation = deploymentParameters.importOptions.getDarFileLocation(build.getWorkspace(), deploymentListener, envVars);
                resolvedDarFileLocation = envVars.expand(darFileLocation);
                deploymentListener.info(Messages.DeployitNotifier_import(resolvedDarFileLocation));
                ConfigurationItem uploadedPackage = deployitServer.importPackage(resolvedDarFileLocation);
                deploymentListener.info(Messages.DeployitNotifier_imported(resolvedDarFileLocation));
                importedVersion = uploadedPackage.getName();
            } catch (Exception e) {
                e.printStackTrace(buildListener.getLogger());
                deploymentListener.error(Messages.DeployitNotifier_import_error(resolvedDarFileLocation, e.getMessage()));
                return false;
            } finally {
                deploymentParameters.importOptions.getMode().cleanup();
            }
        }


        //Deploy
        if (deploymentParameters.deploymentOptions != null) {
            String resolvedEnvironment = envVars.expand(deploymentParameters.deploymentOptions.environment);
            deploymentListener.info(Messages.DeployitNotifier_startDeployment(resolvedApplication, resolvedEnvironment));
            String packageVersion = "";
            switch (deploymentParameters.deploymentOptions.versionKind) {
                case Other:
                    packageVersion = resolvedVersion;
                    break;
                case Packaged:
                    if (!importedVersion.isEmpty()) {
                        packageVersion = importedVersion;
                    } else {
                        packageVersion = deploymentParameters.deploymentOptions.getVersion();
                    }
                    break;
            }
            verifyPackageVersion(packageVersion);
            verifyResolvedApplication(resolvedApplication);

            final String versionId = Joiner.on("/").join(resolvedApplication, packageVersion);
            deploymentListener.info(Messages.DeployitNotifier_deploy(versionId, resolvedEnvironment));
            try {
                deployitServer.deploy(versionId, resolvedEnvironment, deploymentParameters.deploymentOptions, deploymentListener);
            } catch (Exception e) {
                deploymentListener.error(Messages._DeployitNotifier_errorDeploy(e.getMessage()));
                return false;
            }
            deploymentListener.info(Messages.DeployitNotifier_endDeployment(resolvedApplication, resolvedEnvironment));
        }
        return true;
    }


    private void verifyResolvedApplication(String resolvedApplication) {
        if (Strings.isNullOrEmpty(resolvedApplication)) {
            String msg = String.format("Resolved application is '%s'. Please verify you have configured build correctly.", resolvedApplication);
            throw new DeployitPluginException(msg);
        }
    }

    private void verifyPackageVersion(String packageVersion) {
        if (Strings.isNullOrEmpty(packageVersion)) {
            String msg = String.format("Package version is '%s'. Please verify you have configured build correctly.", packageVersion);
            throw new DeployitPluginException(msg);
        }
    }

    private void verifyResolvedVersion(String packageVersion) {
        if (Strings.isNullOrEmpty(packageVersion)) {
            String msg = String.format("Package version is '%s'. Please verify you have configured build correctly.", packageVersion);
            throw new DeployitPluginException(msg);
        }
    }


    static class DeployitPerformerParameters {
        public JenkinsPackageOptions packageOptions;
        public List<PackageProperty> packageProperties;
        public JenkinsImportOptions importOptions;
        public JenkinsDeploymentOptions deploymentOptions;
        public String application;
        public String version;
        public boolean verbose;

        public DeployitPerformerParameters(JenkinsPackageOptions packageOptions, List<PackageProperty> packageProperties, JenkinsImportOptions importOptions, JenkinsDeploymentOptions deploymentOptions,
            String application, String version, boolean verbose) {
            this.packageOptions = packageOptions;
            this.packageProperties = packageProperties;
            this.importOptions = importOptions;
            this.deploymentOptions = deploymentOptions;
            this.application = application;
            this.version = version;
            this.verbose = verbose;
        }
    }
}
