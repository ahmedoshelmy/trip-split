package dev.tripsplit;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/** Cross-cutting beans: the clock (replaceable in tests) and async module listeners. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AppConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Runs module event listeners (timeline, live updates) on virtual threads. */
  @Bean(name = "taskExecutor")
  SimpleAsyncTaskExecutor taskExecutor() {
    var executor = new SimpleAsyncTaskExecutor("module-events-");
    executor.setVirtualThreads(true);
    return executor;
  }
}
