package com.mufeng.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 网关全局防重放校验与全链路 TraceId 注入过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayAttackGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String NONCE_HEADER = "X-Nonce";
    private static final String NONCE_REDIS_PREFIX = "mufeng:gateway:nonce:";

    private final ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 生成或透传全链路追踪 TraceId
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        final String finalTraceId = traceId;

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        // 2. 若请求头携带了防重放随机串 X-Nonce，则执行 Redis 5分钟防重放原子拦截
        String nonce = request.getHeaders().getFirst(NONCE_HEADER);
        if (!StringUtils.hasText(nonce)) {
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        String redisKey = NONCE_REDIS_PREFIX + nonce;
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", Duration.ofMinutes(5))
                .flatMap(success -> {
                    if (Boolean.FALSE.equals(success)) {
                        log.warn("[网关防重放拦截] 检测到重复的 Nonce 请求, URI: {}, Nonce: {}, TraceId: {}",
                                request.getURI().getPath(), nonce, finalTraceId);
                        return writeRejectResponse(exchange.getResponse());
                    }
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    private Mono<Void> writeRejectResponse(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.CONFLICT);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":409,\"message\":\"检测到重复请求(Nonce重放拦截)，请勿重复提交\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}