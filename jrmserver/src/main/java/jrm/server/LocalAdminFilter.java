package jrm.server;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Simple-server filter: elevate unauthenticated {@link jrm.server.shared.WebSession}s to local admin only for loopback peers.
 */
public class LocalAdminFilter implements Filter {

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest)
            LocalAdminAccess.ensureLocalAdmin(httpRequest);
        chain.doFilter(request, response);
    }
}
