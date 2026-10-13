package com.example.orderservice.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Injects the API version from the X-API-Version request header into a controller param.
// Defaults to "v1" when the header is absent.
//
// Usage:
//   @GetMapping("/orders")
//   public List<Order> getOrders(@ApiVersion String version) {
//       if ("v2".equals(version)) { ... }
//   }
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiVersion {
    String defaultVersion() default "v1";
}
