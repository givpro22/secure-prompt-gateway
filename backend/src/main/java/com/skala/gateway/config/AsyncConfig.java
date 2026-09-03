package com.skala.gateway.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 비동기 검사용 스레드 풀 (기획서 8.5).
 *
 * <p>Spring 기본 {@code SimpleAsyncTaskExecutor}는 요청마다 스레드를 새로 만들어 부하 상황에서
 * 스레드가 무제한 증식한다. {@code ThreadPoolTaskExecutor}를 명시한다.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({AiProperties.class, AnswerProperties.class})
public class AsyncConfig implements AsyncConfigurer {

    /** 계약서 §4 인계 3에서 {@code @Async}가 참조하는 executor 이름. */
    public static final String AI_EXECUTOR = "aiExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(AI_EXECUTOR)
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-inspect-");
        // 큐가 차면 호출 스레드에서 실행한다. 검사를 조용히 버리면 inspection이 PENDING에 영구히 남는다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // Mock 지연 2.5초 + 여유. 종료 시 진행 중인 검사가 잘리면 ai_status가 PENDING으로 남는다.
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return aiExecutor();
    }

    /**
     * {@code @Async void} 메서드에서 나간 예외는 호출자에게 전달되지 않고 사라진다.
     * 로그로라도 남긴다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("비동기 실행 중 처리되지 않은 예외: {}", method.getName(), ex);
    }
}
