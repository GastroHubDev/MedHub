package br.com.fiap.agendamento.config;

import br.com.fiap.comum.seguranca.ContextoSeguranca;
import br.com.fiap.comum.seguranca.JsonAccessDeniedHandler;
import br.com.fiap.comum.seguranca.JsonAuthenticationEntryPoint;
import br.com.fiap.comum.seguranca.JwtAuthenticationFilter;
import br.com.fiap.comum.seguranca.JwtProperties;
import br.com.fiap.comum.seguranca.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public TokenService tokenService(JwtProperties propriedades) {
        return new TokenService(propriedades);
    }

    /** Declarado explicitamente: o modulo comum nao usa {@code @Component}. */
    @Bean
    public ContextoSeguranca contextoSeguranca() {
        return new ContextoSeguranca();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenService tokenService) {
        return new JwtAuthenticationFilter(tokenService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ObjectMapper objectMapper) throws Exception {
        return http
                // API stateless com token: nao ha sessao nem cookie para um ataque CSRF explorar.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(tratamento -> tratamento
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
