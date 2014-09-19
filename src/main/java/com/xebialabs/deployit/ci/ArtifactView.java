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
import java.util.List;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import com.google.common.base.Strings;

import com.xebialabs.deployit.ci.dar.RemoteLookup;
import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.util.FileFinder;
import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.udm.artifact.Artifact;
import com.xebialabs.overthere.local.LocalFile;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.RelativePath;
import hudson.util.ListBoxModel;

import static com.xebialabs.deployit.ci.util.ListBoxModels.of;
import static java.lang.String.format;

public class ArtifactView extends DeployableView {

    public String location;

    @DataBoundConstructor
    public ArtifactView(String type, String name, String location, String tags, List<NameValuePair> properties) {
        super(type, name, tags, properties);
        this.location = location;
    }

    @Override
    public ConfigurationItem toConfigurationItem(DeployitDescriptorRegistry registry, FilePath workspace, EnvVars envVars, JenkinsDeploymentListener listener) {
        Artifact deployable = (Artifact) super.toConfigurationItem(registry, workspace, envVars, listener);
        String resolvedLocation = getResolvedLocation(envVars);
        try {
            final File file = findFileFromPattern(resolvedLocation, workspace, listener);
            deployable.setFile(LocalFile.valueOf(file));
        } catch (IOException e) {
            throw new DeployitPluginException(String.format("Unable to find artifact for deployable '%s' in '%s'", getName(), resolvedLocation), e);
        }
        return deployable;
    }

    private String getResolvedLocation(EnvVars envVars) {
        if (Strings.emptyToNull(location) == null) {
            throw new DeployitPluginException(String.format("No location specified for '%s' of type '%s'", getName(), getType()));
        }
        return envVars.expand(location);
    }

    static File findFileFromPattern(String pattern, FilePath workspace, JenkinsDeploymentListener listener) throws IOException {
        listener.info(String.format("Searching for '%s' in '%s'", pattern, workspace));
        FileFinder fileFinder = new FileFinder(pattern);
        List<String> fileNames;
        try {
            fileNames = workspace.act(fileFinder);
        } catch (InterruptedException exception) {
            throw new IOException(format("Interrupted while searching for '%s' in '%s'", pattern, workspace), exception);
        }
        listener.info("Found file(s): " + fileNames);
        if (fileNames.size() > 1) {
            final Localizable localizable = Messages._DeployitNotifier_TooManyFilesMatchingPattern();
            listener.error(localizable);
            throw new DeployitPluginException(String.valueOf(localizable));
        } else if (fileNames.size() == 0) {
            final Localizable localizable = Messages._DeployitNotifier_noArtifactsFound(pattern, workspace);
            listener.error(localizable);
            throw new DeployitPluginException(String.valueOf(localizable));
        }
        // so we use only the first found
        final String artifactPath = fileNames.get(0);
        return fetchFile(artifactPath, workspace);
    }

    private static File fetchFile(String artifactPath, FilePath workspace) throws IOException {
        try {
            return workspace.getChannel().call(new RemoteLookup(artifactPath, workspace.getRemote()));
        } catch (InterruptedException e) {
            throw new DeployitPluginException("Unable to fetch file", e);
        }
    }

    @Extension
    public static final class DescriptorImpl extends DeployableViewDescriptor {

        @Override
        public String getDisplayName() {
            return "Artifact";
        }

        public ListBoxModel doFillTypeItems(
                @QueryParameter(value = "credential") @RelativePath(value = "..") String credentialExistingArtifacts,
                @QueryParameter(value = "credential") @RelativePath(value = "../..") String credentialNewArtifacts
        ) {
            String creds = credentialExistingArtifacts != null ? credentialExistingArtifacts : credentialNewArtifacts;
            return of(getDeployitDescriptor().getAllArtifactTypes(creds));
        }
    }

}
