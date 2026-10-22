package com.example.springcore.scopes;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("singleton") // default — same instance for the entire app lifetime
public class SingletonBean {

    public String getId() {
        return "SingletonBean@" + Integer.toHexString(System.identityHashCode(this));
    }
}
