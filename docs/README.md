| Plugin Information                                                                                              |
|-----------------------------------------------------------------------------------------------------------------|
| View Digital.ai Deploy [on the plugin site](https://plugins.jenkins.io/deployit-plugin) for more information. |

Older versions of this plugin may not be safe to use. Please review the
following warnings before using an older version:

-   [CSRF vulnerability and missing permission check allow
    SSRF](https://jenkins.io/security/advisory/2019-04-17/#SECURITY-983)

The Digital.ai Deploy Plugin integrates Jenkins with [Digital.ai
Deploy](https://docs.digital.ai/bundle/devops-deploy-version-v.10.2) by adding three
post-build actions to your Jenkins installation:

-   Package your application
-   Publish your deployment package to Digital.ai Deploy
-   Deploy your application

These actions can be executed separately or combined sequentially.

# Requirements

-   [A supported version of DAI Deploy.](https://support.digital.ai/hc/en-us/articles/360016879780-XebiaLabs-Supported-Product-Versions#support-overview-0-3) 
-   Jenkins LTS versions supported: from 2.303.1 to 2.319.2
-   Java 8

**Note:** In version 6.0.0 and later, the plugin requires the Jenkins
server run Java 8 or later. Versions 4.0.0 through 5.0.3 require Java 7
or later.

# Features

-   Package a deployment archive (DAR)
    -   With the artifact(s) created by the Jenkins job
    -   With other artifacts or resources
-   Publish DAR packages to XL Deploy
-   Trigger deployments in XL Deploy
-   Auto-scale deployments to modified environments
-   Execute on Microsoft Windows or Unix slave nodes
-   Create a "pipeline as code" in a
    [Jenkinsfile](https://jenkins.io/doc/book/pipeline/jenkinsfile/)
    (supported in version 6.1.0 and later)
-   RollbackOnError option to rollback to the nearest successful build on failure. 
-   Global setting for RollbackOnError which defaults to "true" is introduced. 

For information about configuring and using the plugin, visit the
[Digital.ai documentation
site](https://docs.xebialabs.com/deploy/concept/jenkins-xl-deploy-plugin.html).
