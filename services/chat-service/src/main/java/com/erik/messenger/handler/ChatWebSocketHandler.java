package com.erik.messenger.handler;

import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.Message;
import com.erik.messenger.model.MessageType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException; // NEW IMPORT
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException; // NEW IMPORT
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatMemberRepository chatMemberRepository,
                                MessageRepository messageRepository,
                                ObjectMapper objectMapper) {
        this.chatMemberRepository = chatMemberRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
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

            List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
            for (ChatMember member : members) {
                if (member.getUserId().equals(senderId)) {
                    continue;
                }

                WebSocketSession recipientSession = activeSessions.get(member.getUserId());
                if (recipientSession != null && recipientSession.isOpen()) {
                    try {
                        recipientSession.sendMessage(new TextMessage(message.getPayload()));
                    } catch (IOException e) {
                        System.err.println("Failed to send text message to user " + member.getUserId());
                    }
                }
            }
        }
    }

    // broadcast image uploads (Removed throws Exception)
    public void broadcastMessage(Message message, Object payloadForReact) {
        List<ChatMember> members = chatMemberRepository.findByChatId(message.getChatId());

        try {
            String jsonPayload = objectMapper.writeValueAsString(payloadForReact);

            for (ChatMember member : members) {
                WebSocketSession recipientSession = activeSessions.get(member.getUserId());
                if (recipientSession != null && recipientSession.isOpen()) {
                    try {
                        recipientSession.sendMessage(new TextMessage(jsonPayload));
                    } catch (IOException e) {
                        System.err.println("Failed to broadcast image to user " + member.getUserId());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize image payload: " + e.getMessage());
        }
    }

    // broadcast message deletions (Removed throws Exception)
    public void broadcastMessageDeletion(Long chatId, Long messageId) {
        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);

        try {
            Map<String, Object> deleteEvent = new java.util.HashMap<>();
            deleteEvent.put("type", "DELETE_MESSAGE");
            deleteEvent.put("messageId", messageId);
            deleteEvent.put("chatId", chatId);

            String jsonPayload = objectMapper.writeValueAsString(deleteEvent);

            for (ChatMember member : members) {
                WebSocketSession recipientSession = activeSessions.get(member.getUserId());

                if (recipientSession != null && recipientSession.isOpen()) {
                    try {
                        recipientSession.sendMessage(new TextMessage(jsonPayload));
                    } catch (IOException e) {
                        System.err.println("Failed to broadcast deletion to user " + member.getUserId());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize deletion payload: " + e.getMessage());
        }
    }
}