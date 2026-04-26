package com.erik.messenger.handler;

import com.erik.messenger.model.ChatMember;
import com.erik.messenger.repository.ChatMemberRepository;
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
    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatMemberRepository chatMemberRepository, ObjectMapper objectMapper) {
        this.chatMemberRepository = chatMemberRepository;
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
            
            List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
            
            for (ChatMember member : members) {
                WebSocketSession recipientSession = activeSessions.get(member.getUserId());
                if (recipientSession != null && recipientSession.isOpen()) {
                    recipientSession.sendMessage(new TextMessage(message.getPayload()));
                }
            }
        }
    }
}
