package com.example.notifier.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
public class NotificationListener {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationListener(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "chat-messages", groupId = "notification-group")
    public void consumeMessage(String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            // Only process messages
            if (!"MESSAGE".equals(jsonNode.get("type").asText())) return;

            String content = jsonNode.get("content").asText();
            JsonNode recipientIds = jsonNode.get("recipientIds");

            // Loop through the recipient IDs we cleverly packed into the Kafka message!
            if (recipientIds != null && recipientIds.isArray()) {
                for (JsonNode idNode : recipientIds) {
                    Long targetUserId = idNode.asLong();

                    // Check Redis to see if they are online
                    String status = redisTemplate.opsForValue().get("user:" + targetUserId + ":status");

                    if ("ONLINE".equals(status)) {
                        System.out.println("User " + targetUserId + " is online. Skipping push notification.");
                    } else {
                        System.out.println("User " + targetUserId + " is OFFLINE. Triggering Push Notification!");
                        // TODO: Fire Firebase Push Notification here
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error consuming message: " + e.getMessage());
        }
    }
}