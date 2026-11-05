package dev.relayforge.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class Routes {
    @Bean
    RouteLocator apiRoutes(RouteLocatorBuilder builder,
                           @Value("${relayforge.events-url}") String eventsUrl,
                           @Value("${relayforge.delivery-url}") String deliveryUrl) {
        return builder.routes()
                .route("events", route -> route.path("/api/events", "/api/events/**").uri(eventsUrl))
                .route("deliveries", route -> route.path("/api/deliveries", "/api/deliveries/**").uri(deliveryUrl))
                .build();
    }
}
