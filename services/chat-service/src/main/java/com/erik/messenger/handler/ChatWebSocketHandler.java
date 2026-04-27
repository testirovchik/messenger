package com.erik.messenger.handler;

import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.Message;
import com.erik.messenger.model.MessageType; // Added import for MessageType
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

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
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
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

            // Save message to database
            Message msg = new Message();
            msg.setChatId(chatId);
            msg.setSenderId(senderId);
            msg.setContent(content);

            // --- NEW: Explicitly mark WebSocket messages as TEXT ---
            msg.setType(MessageType.TEXT);

            messageRepository.save(msg);

            // Broadcast to members
            List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
            for (ChatMember member : members) {
                // Skip sending back to the sender
                if (member.getUserId().equals(senderId)) {
                    continue;
                }

                WebSocketSession recipientSession = activeSessions.get(member.getUserId());
                if (recipientSession != null && recipientSession.isOpen()) {
                    recipientSession.sendMessage(new TextMessage(message.getPayload()));
                }
            }
        }
    }

    // --- NEW METHOD: Used by MessageController to broadcast image uploads ---
    public void broadcastMessage(Message message, Object payloadForReact) throws Exception {
        List<ChatMember> members = chatMemberRepository.findByChatId(message.getChatId());

        // Convert the DTO to a JSON string so it can be sent over the WebSocket
        String jsonPayload = objectMapper.writeValueAsString(payloadForReact);

        for (ChatMember member : members) {
            // Notice we do NOT skip the sender here!
            // The sender needs to receive this so their React app can get the Presigned URL
            // and render the image they just uploaded.
            WebSocketSession recipientSession = activeSessions.get(member.getUserId());
            if (recipientSession != null && recipientSession.isOpen()) {
                recipientSession.sendMessage(new TextMessage(jsonPayload));
            }
        }
    }
}