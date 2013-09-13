/**
 * Copyright (c) 2013, XebiaLabs B.V., All rights reserved.
 *
 *
 * The Deployit plugin for Jenkins is licensed under the terms of the GPLv2
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

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;

import com.xebialabs.deployit.booter.remote.client.DeployitRemoteClient;
import com.xebialabs.deployit.ci.dar.RemotePackaging;
import com.xebialabs.deployit.ci.util.DeployitTypes;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.ci.util.NullPackagingListener;
import com.xebialabs.deployit.client.Deployed;
import com.xebialabs.deployit.client.DeployitCli;
import com.xebialabs.deployit.engine.packager.content.DarMember;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.Application;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.Environment;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.uniqueIndex;
import static hudson.util.FormValidation.error;
import static hudson.util.FormValidation.ok;
import static java.util.Collections.emptyList;

public class DeployitNotifier extends Notifier {

    public final String credential;

    public final String application;
    public final String version;

    public final JenkinsPackageOptions packageOptions;
    public final JenkinsImportOptions importOptions;
    public final JenkinsDeploymentOptions deploymentOptions;
    public final boolean verbose;

    protected transient DeployitTypes deployitTypes;

    @DataBoundConstructor
    public DeployitNotifier(String credential, String application, String version, JenkinsPackageOptions packageOptions, JenkinsImportOptions importOptions, JenkinsDeploymentOptions deploymentOptions, boolean verbose) {
        this.credential = credential;
        this.application = application;
        this.version = version;
        this.packageOptions = packageOptions;
        this.importOptions = importOptions;
        this.deploymentOptions = deploymentOptions;
        this.verbose = verbose || (deploymentOptions != null && deploymentOptions.verbose);
    }

    private DeployitTypes getDeployitTypes() {
        if (deployitTypes == null) {
            this.deployitTypes = new DeployitTypes(getCliTemplate().getDescriptors());
        }
        return deployitTypes;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        final JenkinsDeploymentListener deploymentListener = new JenkinsDeploymentListener(listener, verbose);

        final EnvVars envVars = build.getEnvironment(listener);
        String resolvedApplication = envVars.expand(application);
        String resolvedVersion = envVars.expand(version);

        //Package
        if (packageOptions != null) {
            deploymentListener.info(Messages.DeployitNotifier_package(resolvedApplication));
            // package
            deploymentListener.handleInfo("packaged version is " + resolvedVersion);

            final FilePath workspace = build.getWorkspace();
            if (deploymentOptions != null && deploymentOptions.versionKind == VersionKind.Packaged) {
                deploymentOptions.setVersion(resolvedVersion);
            }

            final List<DarMember> darMembers = getDeployablesAsDarMembers(
                    packageOptions.getDeployables(), build.getWorkspace(), envVars, deploymentListener
            );

            final File targetDir = new File(workspace.absolutize().getRemote(), "deployitpackage");

            File packaged = workspace.getChannel().call(
                    new RemotePackaging()
                            .withTargetDir(targetDir)
                            .withListener(verbose ? deploymentListener : new NullPackagingListener())
                            .withDarMembers(darMembers)
                            .forApplication(getCiName(resolvedApplication), resolvedVersion)
            );

            deploymentListener.info(Messages.DeployitNotifier_packaged(resolvedApplication, packaged));
            if (importOptions != null) {
                importOptions.setGeneratedDarLocation(packaged);
            }
        }

        //Import
        String importedVersion = "";
        if (importOptions != null) {

            try {
                final String darFileLocation = importOptions.getDarFileLocation(build.getWorkspace(), deploymentListener);

                deploymentListener.info(Messages.DeployitNofifier_import(darFileLocation));
                ConfigurationItem uploadedPackage = getCliTemplate().perform(new DeployitCliCallback<ConfigurationItem>() {
                    public ConfigurationItem call(DeployitCli cli) {
                        try {
                            ConfigurationItem ci = new DeployitRemoteClient(cli.getCommunicator()).importPackage(darFileLocation);
                            cli.getCommunicator().shutdown();
                            return ci;
                        } catch (Exception e) {
                            deploymentListener.handleError("Importing package failed: " + e.getMessage());
                            throw Throwables.propagate(e);
                        }
                    }
                });
                deploymentListener.info(Messages.DeployitNotifier_imported(darFileLocation));
                importedVersion = uploadedPackage.getName();
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                deploymentListener.error(Messages.DeployitNotifier_import_error(importOptions, e.getMessage()));
                return false;
            } finally {
                try {
                    importOptions.getMode().cleanup();
                } catch (Exception e) {
                    deploymentListener.info(Messages.DeployitNotifier_import_error(importOptions, e.getMessage()));
                }
            }
        }


        //Deploy
        if (deploymentOptions != null) {
            deploymentListener.info(Messages.DeployitNofifier_startDeployment(resolvedApplication, deploymentOptions.environment));
            String packageVersion = null;
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
            final String versionId = Joiner.on("/").join(resolvedApplication, packageVersion);
            deploymentListener.info(Messages.DeployitNofifier_deploy(versionId, deploymentOptions.environment));
            try {
                getCliTemplate().perform(new DeployitCliCallback<String>() {
                    public String call(DeployitCli cli) {
                        return cli.deploy(versionId, deploymentOptions.environment, Collections.<Deployed>emptyList(), deploymentOptions, deploymentListener);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                deploymentListener.error(Messages._DeployitNofifier_errorDeploy(e.getMessage()));
                return false;
            }
            deploymentListener.info(Messages.DeployitNofifier_endDeployment(resolvedApplication, deploymentOptions.environment));
        }
        return true;
    }

    private List<DarMember> getDeployablesAsDarMembers(List<DeployableView> deployables, final FilePath workspace, final EnvVars envVars, final JenkinsDeploymentListener deploymentListener) {
        DeployitTypes deployitTypes = getDeployitTypes();
        if(deployables == null) {
            return emptyList();
        }

        List<DarMember> result = newArrayList();

        for (DeployableView view : deployables) {
            result.add(view.newDarMember(deployitTypes, workspace, envVars, deploymentListener));
        }

        return result;
    }

    private String getCiName(String ciId) {
        return ciId.substring(ciId.lastIndexOf("/") + 1);
    }

    private DeployitCliTemplate getCliTemplate() {
        return getDescriptor().getCliTemplate(credential);
    }

    @Override
    public DeployitDescriptor getDescriptor() {
        return (DeployitDescriptor) super.getDescriptor();
    }

    @Extension
    public static final class DeployitDescriptor extends BuildStepDescriptor<Publisher> {

        private String deployitServerUrl;

        private String deployitClientProxyUrl;

        private List<Credential> credentials = newArrayList();

        private final DeployitCliCache deployitCliCache;

        public DeployitDescriptor() {
            load();
            deployitCliCache = new DeployitCliCache(deployitServerUrl, deployitClientProxyUrl);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            deployitServerUrl = json.get("deployitServerUrl").toString();
            deployitClientProxyUrl = json.get("deployitClientProxyUrl").toString();
            credentials = req.bindJSONToList(Credential.class, json.get("credentials"));
            save();
            deployitCliCache.setDeployitServerUrl(deployitServerUrl);
            deployitCliCache.setDeployitClientProxyUrl(deployitClientProxyUrl);
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

        public List<Credential> getCredentials() {
            return credentials;
        }

        public String getDeployitServerUrl() {
            return deployitServerUrl;
        }

        public String getDeployitClientProxyUrl() {
            return deployitClientProxyUrl;
        }

        public ListBoxModel doFillCredentialItems() {
            ListBoxModel m = new ListBoxModel();
            for (Credential c : credentials)
                m.add(c.name, c.name);
            return m;
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        public List<String> environments(String credential) {
            return getCliTemplate(credential).perform(new DeployitCliCallback<List<String>>() {
                public List<String> call(DeployitCli cli) {
                    return cli.search(Type.valueOf(Environment.class).toString());
                }
            });
        }

        public FormValidation doCheckApplication(@QueryParameter String credential, @QueryParameter final String value, @AncestorInPath AbstractProject project) {
            if ("Applications/".equals(value))
                return ok("Fill in the application ID, eg Applications/PetClinic");

            String resolvedName = null;

            try {
                resolvedName = project.getEnvironment(null, TaskListener.NULL).expand(value);
            } catch (Exception ioe) {
                // Couldn't resolve the app name.
            }

            final String applicationName = resolvedName == null ? value : resolvedName;

            Type udmApplication = Type.valueOf(Application.class);
            try {
                final ConfigurationItem repositoryObject = getCliTemplate(credential).perform(new DeployitCliCallback<ConfigurationItem>() {
                    public ConfigurationItem call(DeployitCli cli) {
                        return cli.get(applicationName);
                    }
                });
                if (repositoryObject.getType().equals(udmApplication))
                    return ok();
                else
                    return error("CI found but with the wrong type, expected %s, found %s", udmApplication, repositoryObject.getType());
            } catch (Exception e) {
                return error("%s. Candidates %s", e.getMessage(), getCandidates(credential, applicationName, udmApplication));
            }
        }

        private Collection<String> getCandidates(String credential, final String value, final Type type) {
            try {
                final List<String> search = getCliTemplate(credential).perform(new DeployitCliCallback<List<String>>() {
                    public List<String> call(DeployitCli cli) {
                        return cli.search(type.toString());
                    }
                });
                return Collections2.filter(search, new Predicate<String>() {
                    public boolean apply(String input) {
                        return input.startsWith(value);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                return emptyList();
            }
        }

        private DeployitCliTemplate getCliTemplate(final String credentialName) {
            final Map<String, Credential> map = uniqueIndex(credentials, Credential.CREDENTIAL_INDEX);

            if (!map.containsKey(credentialName))
                throw new RuntimeException("Credential (" + credentialName + ") not found in " + map.keySet());
            final Credential c = map.get(credentialName);
            try {
                return deployitCliCache.getCliTemplate(c);
            } catch (ExecutionException e) {
                throw new RuntimeException("error", e);
            }
        }

        public List<String> getAllResourceTypes() {
            return deployitCliCache.resources(getDefaultCredential());
        }

        public List<String> getAllArtifactTypes() {
            return deployitCliCache.artifacts(getDefaultCredential());
        }

        public List<String> getPropertiesOf(String type) {
            return deployitCliCache.getPropertiesIndexedByType(getDefaultCredential()).get(type);
        }

        private Credential getDefaultCredential() {
            if (credentials.isEmpty())
                throw new RuntimeException("No credentials defined in the system configuration");
            return credentials.iterator().next();
        }
    }
}
