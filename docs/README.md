| Plugin Information                                                                                              |
|-----------------------------------------------------------------------------------------------------------------|
| View XebiaLabs XL Deploy [on the plugin site](https://plugins.jenkins.io/deployit-plugin) for more information. |

Older versions of this plugin may not be safe to use. Please review the
following warnings before using an older version:

-   [CSRF vulnerability and missing permission check allow
    SSRF](https://jenkins.io/security/advisory/2019-04-17/#SECURITY-983)

The XL Deploy Plugin integrates Jenkins with [XebiaLabs XL
Deploy](https://xebialabs.com/products/xl-deploy) by adding three
post-build actions to your Jenkins installation:

-   Package your application
-   Publish your deployment package to XL Deploy
-   Deploy your application

These actions can be executed separately or combined sequentially.

# Requirements

-   XL Deploy 4.5.0 or later
-   Jenkins LTS version 1.509.1 or later
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

For information about configuring and using the plugin, visit the
[XebiaLabs documentation
site](https://docs.xebialabs.com/deploy/concept/jenkins-xl-deploy-plugin.html).
