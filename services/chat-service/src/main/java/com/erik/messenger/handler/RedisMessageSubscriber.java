package com.erik.messenger.handler;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RedisMessageSubscriber implements MessageListener {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public RedisMessageSubscriber(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // 1. Redis sends the broadcast as bytes, so we convert it back to a JSON String
        String jsonPayload = new String(message.getBody(), StandardCharsets.UTF_8);

        // 2. Pass it to the WebSocket handler to distribute to connected users!
        chatWebSocketHandler.sendToLocalSessions(jsonPayload);
    }
}