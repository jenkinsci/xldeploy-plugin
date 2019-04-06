package com.xebialabs.deployit.ci.action;

import hudson.model.InvisibleAction;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO used to expose xld publish action through jenkins api
 */
@ExportedBean(defaultVisibility = 2)
public class XLDeployPublishAction extends InvisibleAction {
    private String packageId;
    private String packageName;
    private String packageType;
    private String serverUrl;
    private String serverApi;
    private String serverUser;
    private String serverContext;

    private List<ValidationMessage> messages;

    public XLDeployPublishAction () {}

    @Exported
    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Exported
    public String getServerUser() {
        return serverUser;
    }

    public void setServerUser(String serverUser) {
        this.serverUser = serverUser;
    }

    @Exported
    public String getServerApi() {
        return serverApi;
    }

    public void setServerApi(String serverApi) {
        this.serverApi = serverApi;
    }

    @Exported
    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    @Exported
    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Exported
    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    @Exported
    public String getServerContext() {
        return serverContext;
    }

    public void setServerContext(String serverContext) {
        this.serverContext = serverContext;
    }

    @Exported
    public List<ValidationMessage> getMessages () {
        return messages;
    }

    public void addMessage (String ciId, String level, String propertyName, String message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }

        ValidationMessage msg = new ValidationMessage();
        msg.setCiId(ciId);
        msg.setLevel(level);
        msg.setPropertyName(propertyName);
        msg.setMessage(message);

        messages.add(msg);
    }

    public static class ValidationMessage {
        private String ciId;
        private String level;
        private String propertyName;
        private String message;

        public ValidationMessage () {}

        @Exported
        public String getCiId() {
            return ciId;
        }

        public void setCiId(String ciId) {
            this.ciId = ciId;
        }

        @Exported
        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        @Exported
        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        @Exported
        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
