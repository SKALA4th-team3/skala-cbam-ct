package com.skala.cbam.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 202 로 시작하는 작업을 요청 스레드 밖에서 돌린다.
 *
 * <p>지금 쓰는 곳은 22~25번 접수 자료 분석 하나다({@code MailAnalysisService}). AI 호출이 수 초
 * 걸려 요청을 붙들면 담당자는 매칭 버튼을 누르고 화면이 멎은 것을 본다.
 *
 * <p>큐를 짧게 잡았다 — 밀리면 <b>기다리게 두지 않고 거절한다</b>. 실패한 작업은 №19 조회에
 * FAILED 로 남아 담당자가 다시 시도할 수 있지만, 큐에 쌓여 몇 분 뒤에 도는 작업은
 * 아무도 그 결과를 보지 않는다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("applicationTaskExecutor")
    Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("cbam-task-");
        // 종료할 때 돌던 분석이 끝날 시간을 준다 — 반쯤 저장된 상태로 남지 않게
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
