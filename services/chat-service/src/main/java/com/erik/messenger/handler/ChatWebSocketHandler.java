package com.erik.messenger.handler;

import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.Message;
import com.erik.messenger.model.MessageType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
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

    // Notice the @Lazy on StringRedisTemplate to prevent Startup Circular Dependency errors!
    public ChatWebSocketHandler(ChatMemberRepository chatMemberRepository,
                                MessageRepository messageRepository,
                                ObjectMapper objectMapper,
                                @Lazy StringRedisTemplate redisTemplate,
                                ChannelTopic topic) {
        this.chatMemberRepository = chatMemberRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.topic = topic;
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
            Long userId = jsonNode.get("userId").asLong();
            activeSessions.put(userId, session);
        } else if ("MESSAGE".equals(type)) {
            Long chatId = jsonNode.get("chatId").asLong();
            Long senderId = jsonNode.get("senderId").asLong();
            String content = jsonNode.get("content").asText();

            Message msg = new Message();
            msg.setChatId(chatId);
            msg.setSenderId(senderId);
            msg.setContent(content);
            msg.setType(MessageType.TEXT);
            messageRepository.save(msg);

            // PUBLISH TO REDIS INSTEAD OF SENDING DIRECTLY
            redisTemplate.convertAndSend(topic.getTopic(), message.getPayload());
        } else if ("TYPING".equals(type)) {
            Long chatId = jsonNode.get("chatId").asLong();
            Long senderId = jsonNode.get("userId").asLong();

            Map<String, Object> typingEvent = new java.util.HashMap<>();
            typingEvent.put("type", "TYPING");
            typingEvent.put("chatId", chatId);
            typingEvent.put("userId", senderId);

            String typingPayload = objectMapper.writeValueAsString(typingEvent);

            // PUBLISH TO REDIS INSTEAD OF SENDING DIRECTLY
            redisTemplate.convertAndSend(topic.getTopic(), typingPayload);
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