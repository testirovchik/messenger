package com.erik.messenger.config;

import com.erik.messenger.handler.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // 1. Set a default expiration time (e.g., 60 minutes) so Redis doesn't fill up forever
                .entryTtl(Duration.ofMinutes(60))

                // 2. Don't cache empty/null responses
                .disableCachingNullValues()

                // 3. Serialize Keys as plain Strings (e.g., "chatMembers::5")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                // 4. Serialize Values as clean JSON!
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                );
    }

    // 1. Create the Redis Topic (The "Radio Channel")
    @Bean
    public ChannelTopic topic() {
        return new ChannelTopic("chat-events");
    }

    // 2. Configure the Publisher (Used to send messages into Redis)
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    // 3. Configure the Subscriber (Used to listen to Redis)
    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        RedisMessageSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        //just pass the subscriber directly!
        container.addMessageListener(subscriber, topic());

        return container;
    }
}