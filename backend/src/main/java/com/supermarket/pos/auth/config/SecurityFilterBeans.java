package com.supermarket.pos.auth.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The custom security filters are beans (so they can be injected and tested) but must only
 * run inside the Spring Security chain, not a second time as plain servlet filters.
 */
@Configuration
public class SecurityFilterBeans {

    @Bean
    public RateLimitFilter rateLimitFilter(ProblemResponseWriter problems) {
        return new RateLimitFilter(problems);
    }

    @Bean
    public PasswordChangeRequiredFilter passwordChangeRequiredFilter(ProblemResponseWriter problems) {
        return new PasswordChangeRequiredFilter(problems);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PasswordChangeRequiredFilter> passwordChangeFilterRegistration(
            PasswordChangeRequiredFilter filter) {
        FilterRegistrationBean<PasswordChangeRequiredFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
