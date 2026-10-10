package com.example.paymentservice.grpc;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Starts a Netty gRPC server on a configured port and registers every
 * {@link BindableService} bean. Shuts down gracefully on container stop so
 * in-flight RPCs get to finish instead of being torn down.
 *
 * We deliberately do NOT use grpc-spring-boot-starter — hand-rolling 20 lines
 * is more pedagogically useful here and avoids a dependency on a 3rd-party
 * version aligned to our grpc.version.
 */
@Component
public class GrpcServerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final List<BindableService> services;
    private final int port;
    private Server server;

    public GrpcServerLifecycle(List<BindableService> services,
                               @Value("${grpc.server.port:9091}") int port) {
        this.services = services;
        this.port = port;
    }

    @PostConstruct
    public void start() throws IOException {
        ServerBuilder<?> builder = ServerBuilder.forPort(port);
        services.forEach(builder::addService);
        server = builder.build().start();
        log.info("gRPC server started on port {} with {} service(s)", port, services.size());
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            log.info("gRPC server shutting down");
            server.shutdown();
        }
    }
}
