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

import org.kohsuke.stapler.DataBoundConstructor;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;

public class URLLocation extends ImportLocation {
    public final String url;

    @DataBoundConstructor
    public URLLocation(String url) {
        this.url = url;
    }

    @Override
    public String getDarFileLocation(FilePath workspace, JenkinsDeploymentListener deploymentListener, EnvVars envVars) {
        checkNotNull(emptyToNull(url), "URL is empty or null");
        return url;
    }

    @Extension
    public static final class DescriptorImpl extends ImportLocationDescriptor {
        @Override
        public String getDisplayName() {
            return "URL";
        }
    }

    @Override
    public String toString() {
        return String.format("URLLocation[url: %s]", url);
    }
}
