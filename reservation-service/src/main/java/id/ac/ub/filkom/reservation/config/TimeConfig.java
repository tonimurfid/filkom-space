package id.ac.ub.filkom.reservation.config;

import java.time.*;
import org.springframework.context.annotation.*;

@Configuration
public class TimeConfig {
  @Bean
  Clock clock() {
    return Clock.system(ZoneId.of("Asia/Jakarta"));
  }
}
