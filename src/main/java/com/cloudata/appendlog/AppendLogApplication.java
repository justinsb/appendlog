package com.cloudata.appendlog;

import java.util.Set;

import javax.ws.rs.core.Application;

import com.google.common.collect.Sets;

public class AppendLogApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = Sets.newHashSet();
        classes.add(AppendLogD.class);
        classes.add(ByteBufferProvider.class);
        return classes;
    }

}
