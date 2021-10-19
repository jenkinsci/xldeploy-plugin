/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 * <p>
 * <p>
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 * <p>
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.deployit.ci;

import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.google.common.base.Strings;
import com.xebialabs.deployit.ci.DeployitPerformer.DeployitPerformerParameters;
import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.server.DeployitServer;
import com.xebialabs.deployit.ci.server.DeployitServerFactory;
import com.xebialabs.deployit.ci.util.PluginLogger;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static hudson.util.FormValidation.*;

/**
 * Runs XL Deploy tasks after the build has completed.
 */
public class DeployitNotifier extends Notifier {

    public final String credential;
    public final String application;
    public final String version;
    public final JenkinsPackageOptions packageOptions;
    public List<PackageProperty> packageProperties;
    public final JenkinsImportOptions importOptions;
    public final JenkinsDeploymentOptions deploymentOptions;
    public final boolean verbose;

    public Credential overridingCredential;

    public DeployitNotifier(String credential, String application, String version, JenkinsPackageOptions packageOptions, JenkinsImportOptions importOptions, JenkinsDeploymentOptions deploymentOptions, boolean verbose, List<PackageProperty> packageProperties) {
        this(credential, application, version, packageOptions, importOptions, deploymentOptions, verbose, packageProperties, null);
    }

    @DataBoundConstructor
    public DeployitNotifier(String credential, String application, String version, JenkinsPackageOptions packageOptions, JenkinsImportOptions importOptions, JenkinsDeploymentOptions deploymentOptions, boolean verbose, List<PackageProperty> packageProperties, Credential overridingCredential) {
        this.credential = credential;
        this.application = application;
        this.version = version;
        this.packageOptions = packageOptions;
        this.importOptions = importOptions;
        this.deploymentOptions = deploymentOptions;
        this.verbose = verbose;
        this.packageProperties = packageProperties;
        this.overridingCredential = overridingCredential;
        PluginLogger.getInstance().setVerbose(verbose);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        if (credential != null) {
            String cred = credential;
            Credential credential = RepositoryUtils.findCredential(cred);

            if (null != credential && null != overridingCredential) {
                credential = RepositoryUtils.retrieveOverridingCredential(credential, overridingCredential.getCredentialsId(),
                        credential.getName(), overridingCredential.getUsername(), overridingCredential.getPassword(),
                        overridingCredential.isUseGlobalCredential());
            }

            DeployitDescriptor descriptor = RepositoryUtils.getDeployitDescriptor();

            DeployitServer deployitServer = descriptor.getDeployitServer(credential, build.getProject());

            DeployitPerformerParameters performerParameters = new DeployitPerformerParameters(packageOptions, packageProperties, importOptions, deploymentOptions, application, version, verbose);

            DeployitPerformer performer = new DeployitPerformer(build, listener, deployitServer, performerParameters);

            return performer.doPerform();
        } else {
            throw error("Credentials are missing or have not been initialized");
        }


    }

    public boolean showGolbalCredentials() {
        return overridingCredential.isUseGlobalCredential();
    }

    public Credential getOverridingCredential() {
        return this.overridingCredential;
    }

    @Extension
    public static final class DeployitDescriptor extends BuildStepDescriptor<Publisher> {

        // ************ SERIALIZED GLOBAL PROPERTIES *********** //
        private String deployitServerUrl;
        private String deployitClientProxyUrl;

        private int connectionPoolSize = DeployitServer.DEFAULT_POOL_SIZE;

        // credentials are actually globally available credentials
        private List<Credential> credentials = new ArrayList<Credential>();

        private static final SchemeRequirement HTTP_SCHEME = new SchemeRequirement("http");
        private static final SchemeRequirement HTTPS_SCHEME = new SchemeRequirement("https");

        // ************ OTHER NON-SERIALIZABLE PROPERTIES *********** //
        private final transient Map<String, SoftReference<DeployitServer>> credentialServerMap = new HashMap<String, SoftReference<DeployitServer>>();

        public DeployitDescriptor() {
            load();  //deserialize from xml
        }

        private DeployitServer newDeployitServer(Credential credential, ItemGroup<?> itemGroup) {
            String serverUrl = credential.resolveServerUrl(deployitServerUrl);
            String proxyUrl = credential.resolveProxyUrl(deployitClientProxyUrl);

            int newConnectionPoolSize = connectionPoolSize > 0 ? connectionPoolSize : DeployitServer.DEFAULT_POOL_SIZE;
            int socketTimeout = DeployitServer.DEFAULT_SOCKET_TIMEOUT;

            String userName = credential.getUsername();
            String password = credential.getPassword().getPlainText();
            if (credential.isUseGlobalCredential()) {
                StandardUsernamePasswordCredentials cred = Credential.lookupSystemCredentials(credential.getCredentialsId(), itemGroup);
                if (cred == null) {
                    throw new IllegalArgumentException(String.format("Credentials for '%s' not found.", credential.getCredentialsId()));
                }
                userName = cred.getUsername();
                password = cred.getPassword().getPlainText();
            }
            return DeployitServerFactory.newInstance(serverUrl, proxyUrl, userName, password, newConnectionPoolSize, socketTimeout);
        }

        public DeployitServer getDeployitServer(Credential credential, Job<?, ?> project) {
            DeployitServer deployitServer = null;
            if (null != credential) {
                SoftReference<DeployitServer> deployitServerRef = credentialServerMap.get(credential.getKey());

                if (null != deployitServerRef) {
                    deployitServer = deployitServerRef.get();
                }

                if (null == deployitServer) {
                    synchronized (this) {
                        deployitServer = newDeployitServer(credential, project.getParent());
                        credentialServerMap.put(credential.getKey(), new SoftReference<DeployitServer>(deployitServer));
                    }
                }
            }
            // no credential - no server
            return deployitServer;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            //this method is called when the global form is submitted.
            deployitServerUrl = json.get("deployitServerUrl").toString();
            deployitClientProxyUrl = json.get("deployitClientProxyUrl").toString();
            String connectionPoolSizeString = json.get("connectionPoolSize").toString();
            if (!Strings.isNullOrEmpty(connectionPoolSizeString)) {
                connectionPoolSize = Integer.parseInt(connectionPoolSizeString);
            }
            credentials = req.bindJSONToList(Credential.class, json.get("credentials"));
            save();  //serialize to xml
            credentialServerMap.clear(); // each time global config is changed clear server cache
            return true;
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.DeployitNotifier_displayName();
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

        public int getConnectionPoolSize() {
            return connectionPoolSize;
        }

        private FormValidation validateOptionalUrl(String url) {
            try {
                if (!Strings.isNullOrEmpty(url)) {
                    new URL(url);
                }
            } catch (MalformedURLException e) {
                return error("%s is not a valid URL.", url);
            }
            return ok();

        }

        @RequirePOST
        public FormValidation doCheckDeployitServerUrl(@QueryParameter String deployitServerUrl) {

            Jenkins.getInstance().checkPermission(Item.CONFIGURE);
            if (Strings.isNullOrEmpty(deployitServerUrl)) {
                return error("Url required.");
            }
            return validateOptionalUrl(deployitServerUrl);
        }

        @RequirePOST
        public FormValidation doCheckDeployitClientProxyUrl(@QueryParameter String deployitClientProxyUrl) {

            Jenkins.getInstance().checkPermission(Item.CONFIGURE);
            return validateOptionalUrl(deployitClientProxyUrl);
        }

        @RequirePOST
        public FormValidation doCheckConnectionPoolSize(@QueryParameter String connectionPoolSize) {

            Jenkins.getInstance().checkPermission(Item.CONFIGURE);
            if (Strings.isNullOrEmpty(connectionPoolSize)) {
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

        public ListBoxModel doFillCredentialItems() {
            ListBoxModel m = new ListBoxModel();
            for (Credential c : credentials)
                m.add(c.getName(), c.getName());
            return m;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            Jenkins.getInstance().checkPermission(Item.CONFIGURE);
            List<StandardUsernamePasswordCredentials> creds = lookupCredentials(StandardUsernamePasswordCredentials.class, context,
                    ACL.SYSTEM,
                    HTTP_SCHEME, HTTPS_SCHEME);

            return new StandardUsernameListBoxModel().withAll(creds);
        }

        @RequirePOST
        public FormValidation doCheckCredential(@QueryParameter String credential, @AncestorInPath AbstractProject project) {

            project.checkPermission(Item.CONFIGURE);
            DeployitNotifier deployitNotifier = RepositoryUtils.retrieveDeployitNotifierFromProject(project);
            String warningMsg = "Changing credentials may unintentionally change your deployables' types - check the definitions afterward.";
            if (null != deployitNotifier) {
                boolean hasCredential = null != deployitNotifier.credential;
                if (hasCredential && !deployitNotifier.credential.equals(credential)) {
                    return warning("Please Save or Apply changes before you continue. " + warningMsg);
                }
            } else {
                return warning("Please Save or Apply changes before you continue. " + warningMsg);
            }
            return ok();
        }

        @RequirePOST
        public AutoCompletionCandidates doAutoCompleteApplication(@QueryParameter final String value, @AncestorInPath AbstractProject project) {

            project.checkPermission(Item.CONFIGURE);
            String resolvedApplicationName = expandValue(value, project);
            final AutoCompletionCandidates applicationCadidates = new AutoCompletionCandidates();

            final String applicationName = DeployitServerFactory.getNameFromId(resolvedApplicationName);
            DeployitNotifier deployitNotifier = RepositoryUtils.retrieveDeployitNotifierFromProject(project);
            if (deployitNotifier != null) {
                Credential overridingcredential = RepositoryUtils.retrieveOverridingCredentialFromProject(project);

                DeployitServer deployitServer = RepositoryUtils.getDeployitServer(deployitNotifier.credential, overridingcredential, project);
                if (null != deployitServer) {
                    List<String> applicationSuggestions = deployitServer.search(DeployitDescriptorRegistry.UDM_APPLICATION, applicationName + "%");
                    for (String applicationSuggestion : applicationSuggestions) {
                        applicationCadidates.add(applicationSuggestion);
                    }
                }

            }
            return applicationCadidates;
        }

        @RequirePOST
        public FormValidation doCheckApplication(@QueryParameter String credential, @QueryParameter final String value, @AncestorInPath AbstractProject<?, ?> project) {

            project.checkPermission(Item.CONFIGURE);
            if ("Applications/".equals(value))
                return ok("Fill in the application ID, eg Applications/PetClinic");

            String resolvedName = expandValue(value, project);
            final String applicationName = DeployitServerFactory.getNameFromId(resolvedName);

            Credential overridingcredential = RepositoryUtils.retrieveOverridingCredentialFromProject(project);
            DeployitServer deployitServer = RepositoryUtils.getDeployitServer(credential, overridingcredential, project);
            List<String> candidates = deployitServer.search(DeployitDescriptorRegistry.UDM_APPLICATION, applicationName + "%");
            for (String candidate : candidates) {
                if (candidate.endsWith("/" + applicationName)) {
                    return ok();
                }
            }
            if (!candidates.isEmpty()) {
                return warning("Application does not exist, but will be created upon package import. Did you mean to type one of the following: %s?",
                        candidates);
            }
            return warning("Application does not exist, but will be created upon package import.");
        }

        @RequirePOST
        public FormValidation doReloadTypes(@QueryParameter String credential, @AncestorInPath AbstractProject project) {

            project.checkPermission(Item.CONFIGURE);
            Credential overridingcredential = RepositoryUtils.retrieveOverridingCredentialFromProject(project);
            try {
                DeployitServer deployitServer = RepositoryUtils.getDeployitServer(credential, overridingcredential, project);
                if (null == deployitServer)
                    return error("Server not found for credential.");
                deployitServer.reload();
                return ok("Types reloaded from XL Deploy version " + deployitServer.getServerInfo().getVersion() +
                        " at " + deployitServer.getBooterConfig().getUrl());
            } catch (DeployitPluginException ex) {
                return error(String.format("Unable to reload types. Cause: %s.", ex.getMessage()));
            }
        }

        public String expandValue(final String value, final Job project) {
            String resolvedValue = null;

            try {
                resolvedValue = project.getEnvironment(null, TaskListener.NULL).expand(value);
            } catch (Exception ioe) {
                // Couldn't resolve the app name.
            }

            resolvedValue = resolvedValue == null ? value : resolvedValue;
            return resolvedValue;
        }
    }
}
