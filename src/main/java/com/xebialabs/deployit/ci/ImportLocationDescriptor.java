package com.xebialabs.deployit.ci;

import hudson.model.Descriptor;

public abstract class ImportLocationDescriptor extends Descriptor<ImportLocation> {
    @Override
    public String getDisplayName() {
        return "location";
    }

    public ImportLocationDescriptor() {
        super();
    }

    public ImportLocationDescriptor(Class<? extends ImportLocation> clazz) {
        super(clazz);
    }
}
