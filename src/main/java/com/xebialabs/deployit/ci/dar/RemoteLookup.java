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

package com.xebialabs.deployit.ci.dar;

import java.io.File;

import hudson.remoting.Callable;

/**
 * Wrapper for file-related operations.
 * Those must be executed on the target system (where files are located) which may be different
 * from the system where code is executed.
 * @see <a href="https://wiki.jenkins-ci.org/display/JENKINS/Distributed+builds">Jenkins distributed builds</a>
 */
public class RemoteLookup implements Callable<File, RuntimeException> {

    private String artifactPath;

    private String workspacePath;

    public RemoteLookup(final String artifactPath, final String workspacePath) {
        this.artifactPath = artifactPath;
        this.workspacePath = workspacePath;
    }

    @Override
    public File call() throws RuntimeException {
        if (new File(artifactPath).isAbsolute()) {
            return new File(artifactPath);
        }
        return new File(workspacePath, artifactPath);
    }
}
