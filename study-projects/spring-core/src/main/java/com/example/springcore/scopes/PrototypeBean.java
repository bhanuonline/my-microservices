package com.example.springcore.scopes;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype") // new instance every time Spring creates this bean
public class PrototypeBean {

    public String getId() {
        return "PrototypeBean@" + Integer.toHexString(System.identityHashCode(this));
    }
}
