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

package com.xebialabs.deployit.ci;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import com.google.common.collect.Lists;

import hudson.*;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Ordering;

import com.xebialabs.deployit.ci.dar.RemotePackaging;
import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.server.DeployitServerFactory;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static hudson.util.FormValidation.error;
import static hudson.util.FormValidation.ok;
import static hudson.util.FormValidation.warning;

public class DeployitNotifier extends Notifier {

    public final String credential;

    public final String application;
    public final String version;

    public final JenkinsPackageOptions packageOptions;
    public final JenkinsImportOptions importOptions;
    public final JenkinsDeploymentOptions deploymentOptions;
    public final boolean verbose;
    public final List<PackageProperty> packageProperties;

    @DataBoundConstructor
    public DeployitNotifier(String credential, String application, String version, JenkinsPackageOptions packageOptions, JenkinsImportOptions importOptions, JenkinsDeploymentOptions deploymentOptions, boolean verbose, List<PackageProperty> packageProperties) {
        this.credential = credential;
        this.application = application;
        this.version = version;
        this.packageOptions = packageOptions;
        this.importOptions = importOptions;
        this.deploymentOptions = deploymentOptions;
        this.verbose = verbose;
        this.packageProperties = packageProperties;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final JenkinsDeploymentListener deploymentListener = new JenkinsDeploymentListener(listener, verbose);

        final EnvVars envVars = build.getEnvironment(listener);
        String resolvedApplication = envVars.expand(application);

        DeployitServer deployitServer = getDeployitServer();
        if (null == deployitServer) {
            String msg = String.format("No server found for credential %s. Please verify that there is a server configured for credential.", credential);
            throw new DeployitPluginException(msg);
        }

        String applicationName = DeployitServerFactory.getNameFromId(resolvedApplication);
        List<String> qualifiedAppIds = deployitServer.search(DeployitDescriptorRegistry.UDM_APPLICATION, applicationName);
        if (qualifiedAppIds.size() == 1) {
            resolvedApplication = qualifiedAppIds.get(0);
        }
        String resolvedVersion = envVars.expand(version);

        //Package
        if (packageOptions != null) {
            deploymentListener.info(Messages.DeployitNotifier_package(resolvedApplication, resolvedVersion));
            verifyResolvedVersion(resolvedVersion);
            verifyResolvedApplication(resolvedApplication);

            final FilePath workspace = build.getWorkspace();
            if (deploymentOptions != null && deploymentOptions.versionKind == VersionKind.Packaged) {
                deploymentOptions.setVersion(resolvedVersion);
            }

            DeploymentPackage deploymentPackage = packageOptions.toDeploymentPackage(resolvedApplication, resolvedVersion, packageProperties, getDeployitServer().getDescriptorRegistry(), workspace, envVars, deploymentListener);
            final File targetDir = new File(workspace.absolutize().getRemote(), "deployitpackage");

            String packagedPath = workspace.getChannel().call(
                    new RemotePackaging()
                            .withTargetDir(targetDir)
                            .forDeploymentPackage(deploymentPackage)
                            .usingConfig(getDeployitServer().getBooterConfig())
                            .usingDescriptors(Lists.newArrayList(getDeployitServer().getDescriptorRegistry().getDescriptors()))
            );

            deploymentListener.info(Messages.DeployitNotifier_packaged(resolvedApplication, packagedPath));
            if (importOptions != null) {
                importOptions.setGeneratedDarLocation(packagedPath);
            }
        }

        //Import
        String importedVersion = "";
        if (importOptions != null) {
            String resolvedDarFileLocation = "";
            try {
                final String darFileLocation = importOptions.getDarFileLocation(build.getWorkspace(), deploymentListener, envVars);
                resolvedDarFileLocation = envVars.expand(darFileLocation);
                deploymentListener.info(Messages.DeployitNotifier_import(resolvedDarFileLocation));
                ConfigurationItem uploadedPackage = getDeployitServer().importPackage(resolvedDarFileLocation);
                deploymentListener.info(Messages.DeployitNotifier_imported(resolvedDarFileLocation));
                importedVersion = uploadedPackage.getName();
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                deploymentListener.error(Messages.DeployitNotifier_import_error(resolvedDarFileLocation, e.getMessage()));
                return false;
            } finally {
                importOptions.getMode().cleanup();
            }
        }


        //Deploy
        if (deploymentOptions != null) {
            String resolvedEnvironment = envVars.expand(deploymentOptions.environment);
            deploymentListener.info(Messages.DeployitNotifier_startDeployment(resolvedApplication, resolvedEnvironment));
            String packageVersion = "";
            switch (deploymentOptions.versionKind) {
                case Other:
                    packageVersion = resolvedVersion;
                    break;
                case Packaged:
                    if (!importedVersion.isEmpty()) {
                        packageVersion = importedVersion;
                    } else {
                        packageVersion = deploymentOptions.getVersion();
                    }
                    break;
            }
            verifyPackageVersion(packageVersion);
            verifyResolvedApplication(resolvedApplication);

            final String versionId = Joiner.on("/").join(resolvedApplication, packageVersion);
            deploymentListener.info(Messages.DeployitNotifier_deploy(versionId, resolvedEnvironment));
            try {
                getDeployitServer().deploy(versionId, resolvedEnvironment, deploymentOptions, deploymentListener);
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
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

    private DeployitServer getDeployitServer() {
        return getDescriptor().getDeployitServer(credential);
    }

    @Override
    public DeployitDescriptor getDescriptor() {
        return (DeployitDescriptor) super.getDescriptor();
    }

    @Extension
    public static final class DeployitDescriptor extends BuildStepDescriptor<Publisher> {

        // ************ SERIALIZED GLOBAL PROPERTIES *********** //

        private String deployitServerUrl;

        private String deployitClientProxyUrl;

        private int connectionPoolSize = 10;

        private List<Credential> credentials = newArrayList();

        // ************ OTHER NON-SERIALIZABLE PROPERTIES *********** //

        private final transient Map<String,DeployitServer> credentialServerMap = newHashMap();

        public DeployitDescriptor() {
            load();  //deserialize from xml
            mapCredentialsByName();
        }

        private void mapCredentialsByName() {
            for (Credential credential : credentials) {
                String serverUrl = credential.resolveServerUrl(deployitServerUrl);
                String proxyUrl = credential.resolveProxyUrl(deployitClientProxyUrl);

                DeployitServer deployitServer = DeployitServerFactory.newInstance(serverUrl, proxyUrl, credential.username, credential.password.getPlainText());
                int newConnectionPoolSize = connectionPoolSize > 0 ? connectionPoolSize : 10;
                deployitServer.setConnectionPoolSize(newConnectionPoolSize);
                credentialServerMap.put(credential.name, deployitServer);
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            //this method is called when the global form is submitted.
            deployitServerUrl = json.get("deployitServerUrl").toString();
            deployitClientProxyUrl = json.get("deployitClientProxyUrl").toString();
            String connectionPoolSizeString = json.get("connectionPoolSize").toString();
            if(!Strings.isNullOrEmpty(connectionPoolSizeString)) {
                connectionPoolSize = Integer.parseInt(connectionPoolSizeString);
            }
            credentials = req.bindJSONToList(Credential.class, json.get("credentials"));
            save();  //serialize to xml
            mapCredentialsByName();
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.DeployitNotifier_displayName();
        }

        private FormValidation validateOptionalUrl(String url) {
            try {
                if (!Strings.isNullOrEmpty(url)) {
                    new URL(url);
                }
            } catch (MalformedURLException e) {
                return error("%s is not a valid URL.",url);
            }
            return ok();

        }

        public FormValidation doCheckDeployitServerUrl(@QueryParameter String deployitServerUrl) {
            if (Strings.isNullOrEmpty(deployitServerUrl)) {
                return error("Url required.");
            }
            return validateOptionalUrl(deployitServerUrl);
        }

        public FormValidation doCheckDeployitClientProxyUrl(@QueryParameter String deployitClientProxyUrl) {
            return validateOptionalUrl(deployitClientProxyUrl);
        }

        public FormValidation doCheckConnectionPoolSize(@QueryParameter String connectionPoolSize) {
            if(Strings.isNullOrEmpty(connectionPoolSize)) {
                return error("Connection pool size is required.");
            }
            try {
                Integer value = Integer.parseInt(connectionPoolSize);
                if (value <= 0) {
                    return error("Connection pool size may not be negative or zero.");
                }
            } catch (NumberFormatException e) {
                return error("%s is not a valid integer.", connectionPoolSize);
            }

            return ok();
        }

        public FormValidation doReloadTypes(@QueryParameter String credential) {
            DeployitServer deployitServer = getDeployitServer(credential);
            deployitServer.getDescriptorRegistry().reload();
            return ok("Types reloaded from XL Deploy version " + deployitServer.getServerInfo().getVersion() +
                    " at " + deployitServer.getBooterConfig().getUrl());
        }

        public List<Credential> getCredentials() {
            return credentials;
        }

        public String getDeployitServerUrl() {
            return deployitServerUrl;
        }

        public String getDeployitClientProxyUrl() {
            return deployitClientProxyUrl;
        }

        public int getConnectionPoolSize() {return  connectionPoolSize; }

        public ListBoxModel doFillCredentialItems() {
            ListBoxModel m = new ListBoxModel();
            for (Credential c : credentials)
                m.add(c.name, c.name);
            return m;
        }

        public FormValidation doCheckCredential(@QueryParameter String credential) {
            return warning("Changing credentials may unintentionally change your deployables' types - check the definitions afterward");
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        public List<String> environments(final String credential) {
            List<String> envs = getDeployitServer(credential).search(DeployitDescriptorRegistry.UDM_ENVIRONMENT);
            return Ordering.natural().sortedCopy(envs);
        }

        public AutoCompletionCandidates doAutoCompleteApplication(@QueryParameter final String value,
                                                                  @AncestorInPath AbstractProject project)  {
            String resolvedApplicationName = expandValue(value, project);
            final AutoCompletionCandidates applicationCadidates = new AutoCompletionCandidates();
            final String applicationName = DeployitServerFactory.getNameFromId(resolvedApplicationName);
            List<String> applicationSuggestions = getDeployitServer(getDefaultCredential().getName()).search(DeployitDescriptorRegistry.UDM_APPLICATION, applicationName + "%");
            for (String applicationSuggestion : applicationSuggestions) {
                applicationCadidates.add(applicationSuggestion);
            }
            return applicationCadidates;
        }

        public FormValidation doCheckApplication(@QueryParameter String credential, @QueryParameter final String value, @AncestorInPath AbstractProject project) {
            if ("Applications/".equals(value))
                return ok("Fill in the application ID, eg Applications/PetClinic");

            String resolvedName = expandValue(value, project);
            final String applicationName = DeployitServerFactory.getNameFromId(resolvedName);
            List<String> candidates = getDeployitServer(credential).search(DeployitDescriptorRegistry.UDM_APPLICATION, applicationName + "%");
            for (String candidate : candidates) {
                if (candidate.endsWith("/"+applicationName)) {
                    return ok();
                }
            }
            if (!candidates.isEmpty()) {
                return warning("Application does not exist, but will be created upon package import. Did you mean to type one of the following: %s?", candidates);
            }
            return warning("Application does not exist, but will be created upon package import.");
        }

        public String expandValue(final String value, final AbstractProject project) {
            String resolvedValue = null;

            try {
                resolvedValue = project.getEnvironment(null, TaskListener.NULL).expand(value);
            } catch (Exception ioe) {
                // Couldn't resolve the app name.
            }

            resolvedValue = resolvedValue == null ? value : resolvedValue;
            return resolvedValue;
        }

        public DeployitServer getDeployitServer(String credential) {
            checkNotNull(credential);
            return credentialServerMap.get(credential);
        }

        public Collection<String> getAllResourceTypes(String credential) {
            return getDeployitServer(credential).getDescriptorRegistry().getDeployableResourceTypes();
        }

        public Collection<String> getAllEmbeddedResourceTypes(String credential) {
            return getDeployitServer(credential).getDescriptorRegistry().getEmbeddedDeployableTypes();
        }

        public Collection<String> getAllArtifactTypes(String credential) {
            return getDeployitServer(credential).getDescriptorRegistry().getDeployableArtifactTypes();
        }

        public Collection<String> getPropertiesOf(String credential, String type) {
            return getDeployitServer(credential).getDescriptorRegistry().getEditablePropertiesForDeployableType(type);
        }

        private Credential getDefaultCredential() {
            if (credentials.isEmpty())
                throw new DeployitPluginException("No credentials defined in the system configuration");
            return credentials.iterator().next();
        }
    }
}
