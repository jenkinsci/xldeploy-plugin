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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import com.xebialabs.deployit.booter.remote.client.DeployitRemoteClient;
import com.xebialabs.deployit.ci.dar.RemotePackaging;
import com.xebialabs.deployit.ci.util.DeployitTypes;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.client.Deployed;
import com.xebialabs.deployit.client.DeployitCli;
import com.xebialabs.deployit.engine.api.dto.ConfigurationItemId;
import com.xebialabs.deployit.plugin.api.reflect.Type;
import com.xebialabs.deployit.plugin.api.udm.Application;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;
import com.xebialabs.deployit.plugin.api.udm.Environment;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.uniqueIndex;
import static hudson.util.FormValidation.ok;
import static hudson.util.FormValidation.warning;
import static java.lang.String.format;

public class DeployitNotifier extends Notifier {

    public final String credential;

    public final String application;
    public final String version;

    public final JenkinsPackageOptions packageOptions;
    public final JenkinsImportOptions importOptions;
    public final JenkinsDeploymentOptions deploymentOptions;
    public final boolean verbose;


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

    private Type getTypeForClass(Class<?> clazz) {
        return getDescriptor().getTypeForClass(clazz, credential);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final JenkinsDeploymentListener deploymentListener = new JenkinsDeploymentListener(listener, verbose);

        final EnvVars envVars = build.getEnvironment(listener);
        String resolvedApplication = envVars.expand(application);

        List<String> qualifiedAppIds = search(getCliTemplate(), getTypeForClass(Application.class), DeployitTypes.getNameFromId(resolvedApplication));
        if (qualifiedAppIds.size() == 1) {
            resolvedApplication = qualifiedAppIds.get(0);
        }
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

            DeploymentPackage deploymentPackage = packageOptions.toDeploymentPackage(resolvedApplication, resolvedVersion, getCliTemplate().getDeployitTypes(), workspace, envVars, deploymentListener);
            final File targetDir = new File(workspace.absolutize().getRemote(), "deployitpackage");

            File packaged = workspace.getChannel().call(
                    new RemotePackaging()
                            .withTargetDir(targetDir)
                            .forDeploymentPackage(deploymentPackage)
                            .usingConfig(getCliTemplate().getDeployitTypes().getRegistryId())
                            .usingDescriptors(getCliTemplate().getDeployitTypes().getDescriptors().getDescriptors())
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

                deploymentListener.info(Messages.DeployitNotifier_import(darFileLocation));
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
                    deploymentListener.info(Messages.DeployitNotifier_cleanup_error(importOptions.getMode(), e.getMessage()));
                }
            }
        }


        //Deploy
        if (deploymentOptions != null) {
            deploymentListener.info(Messages.DeployitNotifier_startDeployment(resolvedApplication, deploymentOptions.environment));
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
            deploymentListener.info(Messages.DeployitNotifier_deploy(versionId, deploymentOptions.environment));
            try {
                getCliTemplate().perform(new DeployitCliCallback<String>() {
                    public String call(DeployitCli cli) {
                        return cli.deploy(versionId, deploymentOptions.environment, Collections.<Deployed>emptyList(), deploymentOptions, deploymentListener);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                deploymentListener.error(Messages._DeployitNotifier_errorDeploy(e.getMessage()));
                return false;
            }
            deploymentListener.info(Messages.DeployitNotifier_endDeployment(resolvedApplication, deploymentOptions.environment));
        }
        return true;
    }

    public static List<String> search(DeployitCliTemplate template, final Type type, final String name) {
        return template.perform(new DeployitCliCallback<List<String>>() {
            @Override
            public List<String> call(final DeployitCli cli) {
                return search(cli, type, name);
            }
        });
    }

    private static List<String> search(DeployitCli cli, Type type, String name) {
        cli.getListener().debug("search " + type);
        try {
            List<ConfigurationItemId> result = cli.getCommunicator().getProxies().getRepositoryService().query(type, null, name, null, null, 0, -1);

            return Lists.transform(result, new Function<ConfigurationItemId, String>() {
                @Override
                public String apply(ConfigurationItemId input) {
                    return input.getId();
                }
            });
        } catch (Exception e) {
            cli.getListener().debug(format("search fails for %s %s", type, e.getMessage()));
        }
        return Collections.emptyList();
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

        private transient final DeployitCliCache deployitCliCache;

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

        public List<String> environments(final String credential) {
            return getCliTemplate(credential).perform(new DeployitCliCallback<List<String>>() {
                public List<String> call(DeployitCli cli) {
                    return cli.search(getTypeForClass(Environment.class, credential).toString());
                }
            });
        }

        public FormValidation doCheckApplication(@QueryParameter String credential, @QueryParameter final String value, @AncestorInPath AbstractProject project) {
            if ("Applications/".equals(value))
                return ok("Fill in the application name or ID, e.g. Petclinic or Applications/MyDirectory/PetClinic");

            String resolvedName = null;

            try {
                resolvedName = project.getEnvironment(null, TaskListener.NULL).expand(value);
            } catch (Exception ioe) {
                // Couldn't resolve the app name.
            }

            resolvedName = resolvedName == null ? value : resolvedName;
            final String applicationName = DeployitTypes.getNameFromId(resolvedName);
            Type udmApplication = getTypeForClass(Application.class, credential);
            List<String> candidates = search(getCliTemplate(credential), udmApplication, applicationName+"%");
            for (String candidate : candidates) {
                if (candidate.endsWith("/"+applicationName)) {
                    return ok();
                }
            }
            if (!candidates.isEmpty()) {
                return warning("Application does not exist, but will be created upon package import. Did you mean to type one of the following %s?", candidates);
            }
            return warning("Application does not exist, but will be created upon package import.");
        }


        private Credential getCredential(String credentialName) {
            final Map<String, Credential> map = uniqueIndex(credentials, Credential.CREDENTIAL_INDEX);
            return map.get(credentialName);
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

        private Type getTypeForClass(Class<?> clazz, String credentials) {
            return getCliTemplate(credentials).getDeployitTypes().typeForClass(clazz);
        }

        public List<String> getAllResourceTypes() {
            return deployitCliCache.resources(getDefaultCredential());
        }

        public List<String> getAllEmbeddedResourceTypes() {
            return deployitCliCache.embeddedResources(getDefaultCredential());
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
