package ai.zamu.shared.logging;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Gives every request an ID for structured logs and error pages (NFR-16). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = "zamu.requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = "req-" + Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFF_FFFF_FFFFL);
        MDC.put(MDC_KEY, id);
        request.setAttribute(ATTRIBUTE, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
