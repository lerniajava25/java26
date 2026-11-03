package org.example.spring.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.List;

public class ApiKeyFilter extends GenericFilterBean {
    private static final String AUTH_TOKEN_HEADER_NAME = "Authorization";
    private static final String AUTH_TOKEN = "123-456";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        String apiKey = httpRequest.getHeader(AUTH_TOKEN_HEADER_NAME);
        if (apiKey == null || !apiKey.substring(7).equals(AUTH_TOKEN)) {
            throw new BadCredentialsException("Invalid API Key");
        }
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new ApiKeyAuthentication(apiKey.substring(7),
                                List.of()));
        filterChain.doFilter(request, response);
    }
}
