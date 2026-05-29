package com.trilong.kpibackend.core.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            System.out.println("========== [INCOMING REQUEST] ==========");
            System.out.println("Method: " + req.getMethod());
            System.out.println("URI: " + req.getRequestURI());
            System.out.println("Content-Type: " + req.getContentType());
            System.out.println("Content-Length: " + req.getContentLength());
            System.out.println("========================================");
        }
        chain.doFilter(request, response);
    }
}
