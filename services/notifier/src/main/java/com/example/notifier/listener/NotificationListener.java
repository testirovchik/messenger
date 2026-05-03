package com.example.notifier.listener;

import com.example.notifier.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class NotificationListener {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailService emailService; // <-- Inject our new Email Service

    public NotificationListener(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                EmailService emailService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @KafkaListener(topics = "chat-messages", groupId = "notification-group")
    public void consumeMessage(String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (!"MESSAGE".equals(jsonNode.get("type").asText())) return;

            JsonNode recipientIds = jsonNode.get("recipientIds");

            if (recipientIds != null && recipientIds.isArray()) {
                for (JsonNode idNode : recipientIds) {
                    Long targetUserId = idNode.asLong();

                    // 1. Is the user online?
                    String status = redisTemplate.opsForValue().get("user:" + targetUserId + ":status");

                    if ("ONLINE".equals(status)) {
                        System.out.println("User " + targetUserId + " is online. No email needed.");
                    } else {

                        // 2. SPAM CHECK: Did we already send them an email recently?
                        String cooldownKey = "user:" + targetUserId + ":email_cooldown";
                        Boolean inCooldown = redisTemplate.hasKey(cooldownKey);

                        if (Boolean.TRUE.equals(inCooldown)) {
                            System.out.println("User " + targetUserId + " is offline, but is in email cooldown. Skipping.");
                            continue; // Skip to the next user
                        }

                        // 3. Get their email from Redis
                        String emailAddress = redisTemplate.opsForValue().get("user:" + targetUserId + ":email");

                        if (emailAddress != null) {
                            // 4. Send the email!
                            emailService.sendMissedMessageEmail(emailAddress);

                            // 5. Start the 1-hour cooldown!
                            redisTemplate.opsForValue().set(cooldownKey, "TRUE", Duration.ofMinutes(3));
                        } else {
                            System.out.println("User " + targetUserId + " is offline, but we don't have their email in Redis!");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error consuming message: " + e.getMessage());
        }
    }
}