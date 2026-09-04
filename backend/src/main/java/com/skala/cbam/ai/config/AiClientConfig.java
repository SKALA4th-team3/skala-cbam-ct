package com.skala.cbam.ai.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AI 호출 전용 {@link RestClient} (ADR-0012 ①).
 *
 * <p>공용 {@code RestClient.Builder} 를 그대로 쓰지 않는 이유는 <b>타임아웃</b> 때문이다 —
 * AI 응답은 다른 어떤 호출보다 오래 걸린다. 공용 빌더의 값을 AI 에 맞춰 늘리면 다른 호출의
 * 실패가 늦게 드러난다.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    /** 연결은 빨리 포기한다 — 붙지도 못하는 것을 60초 기다릴 이유가 없다. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    RestClient.Builder aiRestClientBuilder(AiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        // 스트리밍이라 이 값은 "다음 조각까지"의 제한이다 — 전체 생성 시간이 아니다 (ADR-0012 ②)
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder().requestFactory(factory);
    }
}
