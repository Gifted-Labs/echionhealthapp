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
 * - templates: 30-minute TTL, max 500 entries (rarely changed report templates)
 * - dashboardStats: 5-minute TTL (admin statistics that don't require real-time
 * accuracy)
 * - notificationCounts: 1-minute TTL (near real-time notification badges)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(defaultCacheBuilder());
        cacheManager.registerCustomCache(USERS, usersCacheBuilder().build());
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
