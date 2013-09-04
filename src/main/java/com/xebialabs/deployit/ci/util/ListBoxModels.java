package com.xebialabs.deployit.ci.util;

import hudson.util.ListBoxModel;

import java.util.List;

public class ListBoxModels {
    public static ListBoxModel of(List<String> types) {
        ListBoxModel m = new ListBoxModel();
        if (types != null) {
            for (String s : types)
                m.add(s, s);
        }
        return m;
    }


    public static ListBoxModel emptyModel() {
        return new ListBoxModel();
    }
}
