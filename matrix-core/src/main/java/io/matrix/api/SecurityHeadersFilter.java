package io.matrix.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS response filter that adds security headers to all HTTP responses.
 *
 * <p>Headers added:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME type sniffing</li>
 *   <li>{@code X-Frame-Options: DENY} — prevents clickjacking via iframes</li>
 *   <li>{@code X-XSS-Protection: 1; mode=block} — enables browser XSS filter</li>
 *   <li>{@code Strict-Transport-Security} — enforces HTTPS (max-age=1 year)</li>
 *   <li>{@code Content-Security-Policy} — restricts resource loading</li>
 *   <li>{@code Referrer-Policy} — controls referrer information leakage</li>
 *   <li>{@code Permissions-Policy} — restricts browser feature access</li>
 * </ul>
 *
 * <p>Applied to all responses automatically via {@code @Provider}.
 */
@Provider
@Priority(Integer.MIN_VALUE) // Execute first to ensure headers are always present
public class SecurityHeadersFilter implements ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();

        // Prevent MIME type sniffing
        headers.putSingle(HEADER_X_CONTENT_TYPE_OPTIONS, "nosniff");

        // Prevent clickjacking
        headers.putSingle(HEADER_X_FRAME_OPTIONS, "DENY");

        // Enable XSS protection (legacy browsers)
        headers.putSingle(HEADER_X_XSS_PROTECTION, "1; mode=block");

        // Enforce HTTPS for 1 year (includeSubDomains for HSTS preload)
        headers.putSingle(HEADER_STRICT_TRANSPORT_SECURITY,
                "max-age=31536000; includeSubDomains");

        // Content Security Policy — restrict to same-origin
        headers.putSingle(HEADER_CONTENT_SECURITY_POLICY,
                "default-src 'self'; frame-ancestors 'none'");

        // Control referrer information
        headers.putSingle(HEADER_REFERRER_POLICY, "strict-origin-when-cross-origin");

        // Restrict browser features
        headers.putSingle(HEADER_PERMISSIONS_POLICY,
                "camera=(), microphone=(), geolocation=(), payment=()");

        if (log.isTraceEnabled()) {
            log.trace("Security headers applied to {} {}",
                    requestContext.getMethod(), requestContext.getUriInfo().getPath());
        }
    }
}
