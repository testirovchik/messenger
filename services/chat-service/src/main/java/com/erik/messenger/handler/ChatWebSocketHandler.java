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

    // Notice the @Lazy on StringRedisTemplate to prevent Startup Circular Dependency errors!
    public ChatWebSocketHandler(ChatMemberRepository chatMemberRepository,
                                MessageRepository messageRepository,
                                ObjectMapper objectMapper,
                                JwtService jwtService,
                                @Lazy StringRedisTemplate redisTemplate,
                                ChannelTopic topic,
                                KafkaTemplate<String, String> kafkaTemplate) {
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
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String type = jsonNode.get("type").asText();

        if ("REGISTER".equals(type)) {
            try {
                // 1. Force the user to send their JWT token!
                String token = jsonNode.get("token").asText();

                // 2. Extract and verify their true identity
                Long realUserId = jwtService.extractUserIdFromToken(token);

                // 3. SECURE THE SESSION: Stamp their real ID directly into the server's session memory
                session.getAttributes().put("SECURE_USER_ID", realUserId);
                activeSessions.put(realUserId, session);

                System.out.println("User " + realUserId + " securely connected to WebSockets.");

            } catch (Exception e) {
                // If the token is fake, expired, or missing, slam the door!
                System.err.println("HACK ATTEMPT: Invalid WebSocket JWT Token.");
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid JWT Token"));
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

            // 1. Save to Database and CAPTURE the saved entity to get the real ID and Timestamp
            Message msg = new Message();
            msg.setChatId(chatId);
            msg.setSenderId(secureSenderId);
            msg.setContent(content);
            msg.setType(MessageType.TEXT);
            msg = messageRepository.save(msg); // <--- Reassign to get the generated ID!

            // 2. Build the rich JSON payload for the frontend and Kafka
            Map<String, Object> responsePayload = new java.util.HashMap<>();
            responsePayload.put("type", "MESSAGE");
            responsePayload.put("id", msg.getId());
            responsePayload.put("chatId", msg.getChatId());
            responsePayload.put("senderId", msg.getSenderId());
            responsePayload.put("content", msg.getContent());
            responsePayload.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : java.time.LocalDateTime.now().toString());

            String jsonToSend = objectMapper.writeValueAsString(responsePayload);

            // 3. Broadcast via Redis for instant WebSocket delivery to User B
            redisTemplate.convertAndSend(topic.getTopic(), jsonToSend);

            // 4. NEW: Send to Kafka using the Chat ID as the Partition Key!
            String partitionKey = String.valueOf(msg.getChatId());
            kafkaTemplate.send("chat-messages", partitionKey, jsonToSend);

            System.out.println("Successfully sent message to Kafka (Partition Key: " + partitionKey + ")");
        }

        else if ("TYPING".equals(type)) {
            // Secure the typing indicator too!
            Long secureSenderId = (Long) session.getAttributes().get("SECURE_USER_ID");
            Long claimedSenderId = jsonNode.get("userId").asLong();

            if (secureSenderId == null || !secureSenderId.equals(claimedSenderId)) {
                return; // Silently drop fake typing events
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

    // MASTER SENDER METHOD (Triggered by RedisMessageSubscriber)
    public void sendToLocalSessions(String jsonPayload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonPayload);
            Long chatId = jsonNode.get("chatId").asLong();
            String type = jsonNode.get("type").asText();

            // Extract sender ID depending on how it was packed in the JSON
            Long senderId = null;
            if (jsonNode.has("senderId")) {
                senderId = jsonNode.get("senderId").asLong();
            } else if (jsonNode.has("userId")) {
                senderId = jsonNode.get("userId").asLong();
            }

            List<ChatMember> members = chatMemberRepository.findByChatId(chatId);

            for (ChatMember member : members) {
                // If it is a normal MESSAGE or a TYPING event, we DO NOT send it back to the person who sent it.
                // But for IMAGE, DELETE, or EDIT, the sender needs to receive it to update their own React screen!
                if (senderId != null && member.getUserId().equals(senderId)) {
                    if ("MESSAGE".equals(type) || "TYPING".equals(type)) {
                        continue;
                    }
                }

                // Check ONLY in this specific server's HashMap
                WebSocketSession recipientSession = activeSessions.get(member.getUserId());
                if (recipientSession != null && recipientSession.isOpen()) {
                    try {
                        recipientSession.sendMessage(new TextMessage(jsonPayload));
                    } catch (IOException e) {
                        System.err.println("Failed to route to user " + member.getUserId());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing Redis message: " + e.getMessage());
        }
    }
}