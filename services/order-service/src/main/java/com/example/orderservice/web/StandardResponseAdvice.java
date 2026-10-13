package com.example.orderservice.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;

// Intercepts every @RestController response body BEFORE it is serialised to JSON.
// Scoped to our controllers only — actuator, swagger, error pages are excluded.
//
// ResponseBodyAdvice<T>:
//   T = the type this advice can wrap (Object = handle everything)
@RestControllerAdvice(basePackages = "com.example.orderservice.controller")
public class StandardResponseAdvice implements ResponseBodyAdvice<Object> {

    // supports() — return true to tell Spring this advice should run for this response
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Skip if already wrapped — prevents double-wrapping
        return !returnType.getParameterType().equals(StandardResponse.class);
    }

    // beforeBodyWrite() — transform the body before serialisation
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // Extract correlationId set by CorrelationIdInterceptor
        String correlationId = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            correlationId = (String) httpRequest.getAttribute("correlationId");
            if (correlationId == null) {
                correlationId = response.getHeaders().getFirst(CorrelationIdInterceptor.HEADER);
            }
        }

        int status = response.getHeaders().containsKey("status")
                ? Integer.parseInt(response.getHeaders().getFirst("status"))
                : 200;

        return StandardResponse.of(body, status, correlationId);
    }
}
