package com.xebialabs.deployit.ci;

public enum VersionKind {

    Packaged("From this job"), Other("Other (please specify above)");

    private final String label;

    VersionKind(String kind) {
        this.label = kind;
    }

    public String getLabel() {
        return label;
    }
}
