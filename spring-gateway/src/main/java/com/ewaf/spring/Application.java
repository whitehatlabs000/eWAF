package com.ewaf.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;
import java.net.URI;

@SpringBootApplication
public class Application {
    
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        log.info("🚀 eWAF Spring Cloud Gateway Node is RUNNING on port 8082");
    }

    // 1. Ruta base requerida por Spring. Atrapa todo pero el host es falso.
    @Bean
    public RouteLocator dynamicRouter(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("ewaf_dynamic_route", r -> r.path("/**")
            .uri("http://dummy-host-no-usado")) 
            .build();
    }

    // 2. Filtro Global: Se ejecuta al FINAL de toda la cadena de Spring.
    // Nadie podrá sobrescribir nuestra URL después de esto.
    @Bean
    public GlobalFilter customRoutingFilter() {
        return new GlobalFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                String targetUrl = exchange.getRequest().getHeaders().getFirst("X-Target-Url");
                
                if (targetUrl != null && !targetUrl.isEmpty()) {
                    try {
                        String finalUrl = targetUrl;
                        // Quitamos la barra final de la base si la tiene para no duplicarla
                        if (finalUrl.endsWith("/")) {
                            finalUrl = finalUrl.substring(0, finalUrl.length() - 1);
                        }
                        
                        String path = exchange.getRequest().getURI().getRawPath();
                        String query = exchange.getRequest().getURI().getRawQuery();
                        String fullUrl = finalUrl + path + (query != null ? "?" + query : "");
                        
                        URI uri = new URI(fullUrl);

                        // SOBRESCRIBIR HEADERS (ANTI-BLOCKING PARA SPAs COMO GITHUB)
                        // GitHub bloquea peticiones si el Host u Origin es "localhost".
                        // Clonamos la petición original y le inyectamos los pasaportes correctos.
                        org.springframework.http.server.reactive.ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("Host", uri.getHost())
                                .header("Origin", uri.getScheme() + "://" + uri.getHost())
                                .build();

                        // Sobrescribimos el destino final justo antes de salir
                        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, uri);
                        log.debug("🎯 Spring Gateway sending final package to: {}", uri);

                        // Retornamos la cadena con la petición falsificada
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    } catch (Exception e) {
                        log.error("Error constructing final URI: ", e);
                    }
                }
                return chain.filter(exchange);
            }
        };
    }
}