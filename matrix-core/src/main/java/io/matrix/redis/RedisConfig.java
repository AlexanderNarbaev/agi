package io.matrix.redis;

import io.lettuce.core.RedisClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * CDI configuration that produces a {@link RedisClient} bean
 * for the Lettuce Redis client library.
 *
 * <p>Connection URI is read from {@code redis.uri} MicroProfile Config property
 * with default {@code redis://localhost:6379}.
 */
@ApplicationScoped
public class RedisConfig {

    @Produces
    @Singleton
    public RedisClient redisClient() {
        String uri = ConfigProvider.getConfig()
                .getOptionalValue("redis.uri", String.class)
                .orElse("redis://localhost:6379");
        return RedisClient.create(uri);
    }
}
