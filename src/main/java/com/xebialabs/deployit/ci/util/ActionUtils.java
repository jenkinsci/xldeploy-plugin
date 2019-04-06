package com.xebialabs.deployit.ci.util;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.ci.action.XLDeployPublishAction;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.deployit.plugin.api.validation.ValidationMessage;
import hudson.model.Run;

import java.util.List;

/**
 * Utility class used to feed and add workflow actions to the running job
 */
public class ActionUtils {
    public static void addPublishAction(Run<?,?> run, BooterConfig config, ConfigurationItem importedPackage) {
        XLDeployPublishAction action = new XLDeployPublishAction();
        action.setServerUrl(config.getUrl());
        action.setServerUser(config.getUsername());
        action.setServerApi(config.getExtensionApiUrl());
        action.setServerContext(config.getContext());
        action.setPackageId(importedPackage.getId());
        action.setPackageName(importedPackage.getName());
        action.setPackageType(importedPackage.getType().getPrefix() + "." + importedPackage.getType().getName());

        List<ValidationMessage> messages = importedPackage.get$validationMessages();
        if (messages != null) {
            importedPackage.get$validationMessages().forEach(message -> {
                action.addMessage(message.getCiId(), message.getLevel().name(), message.getPropertyName(), message.getMessage());
            });
        }

        run.addAction(action);
    }
}
