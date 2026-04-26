package com.erik.messenger.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        userSessions.values().remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String type = jsonNode.get("type").asText();

        if ("REGISTER".equals(type)) {
            Long userId = jsonNode.get("userId").asLong();
            userSessions.put(userId, session);
            session.getAttributes().put("userId", userId);
        } else if ("MESSAGE".equals(type)) {
            Long receiverId = jsonNode.get("receiverId").asLong();
            String content = jsonNode.get("content").asText();
            Long senderId = (Long) session.getAttributes().get("userId");

            WebSocketSession receiverSession = userSessions.get(receiverId);
            if (receiverSession != null && receiverSession.isOpen()) {
                ObjectNode responseNode = objectMapper.createObjectNode();
                responseNode.put("senderId", senderId);
                responseNode.put("content", content);
                responseNode.put("type", "MESSAGE");
                
                receiverSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(responseNode)));
            }
        }
    }
}
