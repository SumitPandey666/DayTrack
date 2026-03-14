package com.monkmode.ledger.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // If there is no user in the session, block the request
        if (request.getSession().getAttribute("USER_ID") == null) {

            // HTMX background requests require a special header to force a redirect
            if (request.getHeader("HX-Request") != null) {
                response.setHeader("HX-Redirect", "/login");
            } else {
                // Standard browser redirect
                response.sendRedirect("/login");
            }
            return false; // Block execution
        }
        return true; // Let them through
    }
}