package com.giftedlabs.echoinhealthbackend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

import static com.giftedlabs.echoinhealthbackend.util.CacheNames.*;

/**
 * Cache configuration using Caffeine for high-performance in-memory caching.
 * 
 * Cache strategies:
 * - users: 15-minute TTL, max 1000 entries (frequently accessed user profiles)
 * - authUsers: 14-minute TTL, max 10,000 entries (JWT-authenticated principals)
 * - templates: 30-minute TTL, max 500 entries (rarely changed report templates)
 * - dashboardStats: 5-minute TTL (admin statistics that don't require real-time
 * accuracy)
 * - notificationCounts: 1-minute TTL (near real-time notification badges)
 */
@Configuration
// proxyTargetClass matches Spring Boot's own AOP default. Stating it explicitly keeps
// class-based proxies in narrower contexts (such as slice tests) too, so beans that are
// injected by concrete type — CustomUserDetailsService, for one — resolve consistently.
@EnableCaching(proxyTargetClass = true)
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(defaultCacheBuilder());
        cacheManager.registerCustomCache(USERS, usersCacheBuilder().build());
        cacheManager.registerCustomCache(AUTH_USERS, authUsersCacheBuilder().build());
        cacheManager.registerCustomCache(TEMPLATES, templatesCacheBuilder().build());
        cacheManager.registerCustomCache(DASHBOARD_STATS, dashboardStatsCacheBuilder().build());
        cacheManager.registerCustomCache(NOTIFICATION_COUNTS, notificationCountsCacheBuilder().build());
        return cacheManager;
    }

    /**
     * Default cache configuration - 10 minute TTL, max 500 entries
     */
    private Caffeine<Object, Object> defaultCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats();
    }

    /**
     * User cache - 15 minute TTL, max 1000 entries
     * Used for user profile lookups by email and ID
     */
    private Caffeine<Object, Object> usersCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats();
    }

    /**
     * Authenticated user cache - 14 minute TTL to stay below JWT expiry.
     * Used by JWT authentication to avoid hitting the database on every request.
     */
    private Caffeine<Object, Object> authUsersCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(14, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .recordStats();
    }

    /**
     * Templates cache - 30 minute TTL, max 500 entries
     * Report templates are rarely modified
     */
    private Caffeine<Object, Object> templatesCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats();
    }

    /**
     * Dashboard stats cache - 5 minute TTL, single entry per admin
     * Statistics don't require real-time accuracy
     */
    private Caffeine<Object, Object> dashboardStatsCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(10)
                .recordStats();
    }

    /**
     * Notification counts cache - 1 minute TTL
     * Near real-time but reduces DB load for badge updates
     */
    private Caffeine<Object, Object> notificationCountsCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats();
    }
}
