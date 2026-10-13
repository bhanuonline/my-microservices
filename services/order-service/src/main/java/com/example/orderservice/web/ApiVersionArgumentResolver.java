package com.example.orderservice.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ApiVersionArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String VERSION_HEADER = "X-API-Version";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ApiVersion.class)
                && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        String version = request != null ? request.getHeader(VERSION_HEADER) : null;

        if (version == null || version.isBlank()) {
            // Fall back to the default declared on the annotation: @ApiVersion(defaultVersion="v2")
            ApiVersion annotation = parameter.getParameterAnnotation(ApiVersion.class);
            version = annotation != null ? annotation.defaultVersion() : "v1";
        }

        return version.toLowerCase().startsWith("v") ? version : "v" + version;
    }
}
