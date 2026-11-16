package id.ac.ub.filkom.reservation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ub.filkom.reservation.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(
      @Value("${app.admin-password}") String admin,
      @Value("${app.student-password}") String student,
      @Value("${app.student-id}") String studentId) {
    var encoder = new BCryptPasswordEncoder();
    return new InMemoryUserDetailsManager(
        User.withUsername("admin").password(encoder.encode(admin)).roles("ADMIN").build(),
        User.withUsername(studentId).password(encoder.encode(student)).roles("STUDENT").build());
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/openapi.yaml")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/availability", "/api/v2/availability")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/reservations", "/api/v2/reservations")
                    .hasAnyRole("STUDENT", "ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/reservations/*")
                    .hasAnyRole("STUDENT", "ADMIN")
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/reservations", "/api/v1/reservations/*",
                        "/api/v2/reservations", "/api/v2/reservations/*")
                    .hasAnyRole("ADMIN", "STUDENT")
                    .anyRequest()
                    .denyAll())
        .httpBasic(
            b ->
                b.authenticationEntryPoint(
                    (r, s, e) -> {
                      s.setStatus(401);
                      s.setHeader("WWW-Authenticate", "Basic realm=\"FILKOM Space\"");
                      s.setContentType("application/json");
                      mapper.writeValue(
                          s.getOutputStream(),
                          new ErrorResponse(
                              401, "UNAUTHORIZED", "Autentikasi diperlukan.", r.getRequestURI()));
                    }))
        .exceptionHandling(
            e ->
                e.accessDeniedHandler(
                    (r, s, x) -> {
                      s.setStatus(403);
                      s.setContentType("application/json");
                      mapper.writeValue(
                          s.getOutputStream(),
                          new ErrorResponse(403, "FORBIDDEN", "Akses ditolak.", r.getRequestURI()));
                    }))
        .build();
  }
}
