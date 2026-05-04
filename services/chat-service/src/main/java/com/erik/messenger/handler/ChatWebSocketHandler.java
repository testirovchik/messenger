package com.erik.messenger.handler;

import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.Message;
import com.erik.messenger.model.MessageType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.MessageRepository;
import com.erik.messenger.service.JwtService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // NEW: Inject Redis Publisher tools
    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic topic;
    private final JwtService jwtService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private final Counter messagesSentCounter;

    // Notice the @Lazy on StringRedisTemplate to prevent Startup Circular Dependency errors!
    public ChatWebSocketHandler(ChatMemberRepository chatMemberRepository,
                                MessageRepository messageRepository,
                                ObjectMapper objectMapper,
                                JwtService jwtService,
                                @Lazy StringRedisTemplate redisTemplate,
                                ChannelTopic topic,
                                KafkaTemplate<String, String> kafkaTemplate,
                                MeterRegistry meterRegistry) {
        this.messagesSentCounter = meterRegistry.counter("business_messages_sent_total", "type", "websocket");
        this.chatMemberRepository = chatMemberRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
        Long userId = (Long) session.getAttributes().get("SECURE_USER_ID");
        if (userId != null) {
            redisTemplate.delete("user:" + userId + ":status");
            System.out.println("User " + userId + " disconnected. Removed from Redis.");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String type = jsonNode.get("type").asText();

        if ("REGISTER".equals(type)) {
            try {
                String token = jsonNode.get("token").asText();
                String email = jsonNode.get("email").asText();

                Long realUserId = jwtService.extractUserIdFromToken(token);

                session.getAttributes().put("SECURE_USER_ID", realUserId);
                activeSessions.put(realUserId, session);

                redisTemplate.opsForValue().set("user:" + realUserId + ":status", "ONLINE");
                redisTemplate.opsForValue().set("user:" + realUserId + ":email", email);

                System.out.println("User " + realUserId + " securely connected and cached in Redis with email: " + email);

            } catch (Exception e) {
                // If the token is fake, expired, or missing, slam the door!
                System.err.println("HACK ATTEMPT: Invalid WebSocket Payload (Bad JWT or Missing Email).");
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid Payload"));
            }
        }

        else if ("MESSAGE".equals(type)) {
            // Pull their verified identity from session memory
            Long secureSenderId = (Long) session.getAttributes().get("SECURE_USER_ID");
            Long claimedSenderId = jsonNode.get("senderId").asLong();

            // Did they authenticate? Are they trying to impersonate someone else?
            if (secureSenderId == null || !secureSenderId.equals(claimedSenderId)) {
                System.err.println("SECURITY BREACH: User tried to spoof senderId " + claimedSenderId);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Identity spoofing detected"));
                return; // Stop the code right here
            }

            // If they pass the check, process the message normally!
            Long chatId = jsonNode.get("chatId").asLong();
            String content = jsonNode.get("content").asText();

            boolean isMember = chatMemberRepository.existsByChatIdAndUserId(chatId, secureSenderId);
            if (!isMember) {
                System.err.println("SECURITY BREACH: User " + secureSenderId + " tried to message Chat " + chatId + " without being a member.");
                return;
            }

            Message msg = new Message();
            msg.setChatId(chatId);
            msg.setSenderId(secureSenderId);
            msg.setContent(content);
            msg.setType(MessageType.TEXT);
            msg = messageRepository.save(msg); // <--- Reassign to get the generated ID!

            messagesSentCounter.increment();
            List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
            List<Long> recipientIds = members.stream()
                    .map(ChatMember::getUserId)
                    .filter(id -> !id.equals(secureSenderId)) // Exclude the sender
                    .toList();

            Map<String, Object> responsePayload = new java.util.HashMap<>();
            responsePayload.put("type", "MESSAGE");
            responsePayload.put("id", msg.getId()); // <-- MISSING
            responsePayload.put("chatId", msg.getChatId());
            responsePayload.put("senderId", msg.getSenderId());
            responsePayload.put("content", msg.getContent());
            responsePayload.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());
            responsePayload.put("recipientIds", recipientIds);

            String jsonToSend = objectMapper.writeValueAsString(responsePayload);

            redisTemplate.convertAndSend(topic.getTopic(), jsonToSend);
            System.out.println("Broadcasted message to Redis Pub/Sub for live delivery!");

            String partitionKey = String.valueOf(msg.getChatId());
            kafkaTemplate.send("chat-messages", partitionKey, jsonToSend);

            System.out.println("Successfully sent message to Kafka (Partition Key: " + partitionKey + ")");
        }

        else if ("TYPING".equals(type)) {
            // Secure the typing indicator too!
            Long secureSenderId = (Long) session.getAttributes().get("SECURE_USER_ID");
            Long claimedSenderId = jsonNode.get("userId").asLong();

            if (secureSenderId == null || !secureSenderId.equals(claimedSenderId)) {
                return;
            }

            redisTemplate.convertAndSend(topic.getTopic(), message.getPayload());
        }
    }

    public void broadcastMessage(Message message, Object payloadForReact) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payloadForReact);
            // PUBLISH TO REDIS
            redisTemplate.convertAndSend(topic.getTopic(), jsonPayload);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize image payload: " + e.getMessage());
        }
    }

    public void broadcastMessageDeletion(Long chatId, Long messageId) {
        try {
            Map<String, Object> deleteEvent = new java.util.HashMap<>();
            deleteEvent.put("type", "DELETE_MESSAGE");
            deleteEvent.put("messageId", messageId);
            deleteEvent.put("chatId", chatId);

            String jsonPayload = objectMapper.writeValueAsString(deleteEvent);
            // PUBLISH TO REDIS
            redisTemplate.convertAndSend(topic.getTopic(), jsonPayload);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize deletion payload: " + e.getMessage());
        }
    }

    public void broadcastMessageEdit(Long chatId, Long messageId, String newContent) {
        try {
            Map<String, Object> editEvent = new java.util.HashMap<>();
            editEvent.put("type", "EDIT_MESSAGE");
            editEvent.put("messageId", messageId);
            editEvent.put("chatId", chatId);
            editEvent.put("newContent", newContent);

            String jsonPayload = objectMapper.writeValueAsString(editEvent);
            // PUBLISH TO REDIS
            redisTemplate.convertAndSend(topic.getTopic(), jsonPayload);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize edit payload: " + e.getMessage());
        }
    }

    //send the message to this server's recepients
    public void sendToLocalSessions(String jsonPayload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonPayload);
            String type = jsonNode.get("type").asText();

            // Extract sender ID
            Long senderId = null;
            if (jsonNode.has("senderId")) {
                senderId = jsonNode.get("senderId").asLong();
            } else if (jsonNode.has("userId")) {
                senderId = jsonNode.get("userId").asLong();
            }

            // INSTEAD OF THE DATABASE: Just grab the array we packed into the JSON!
            JsonNode recipientIdsNode = jsonNode.get("recipientIds");

            if (recipientIdsNode != null && recipientIdsNode.isArray()) {
                for (JsonNode idNode : recipientIdsNode) {
                    Long targetUserId = idNode.asLong();

                    // Same logic: don't send normal messages back to the sender
                    if (senderId != null && targetUserId.equals(senderId)) {
                        if ("MESSAGE".equals(type) || "TYPING".equals(type)) {
                            continue;
                        }
                    }

                    // Check ONLY in this specific server's HashMap
                    WebSocketSession recipientSession = activeSessions.get(targetUserId);
                    if (recipientSession != null && recipientSession.isOpen()) {
                        try {
                            recipientSession.sendMessage(new TextMessage(jsonPayload));
                        } catch (IOException e) {
                            System.err.println("Failed to route to user " + targetUserId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing Redis message: " + e.getMessage());
        }
    }
}