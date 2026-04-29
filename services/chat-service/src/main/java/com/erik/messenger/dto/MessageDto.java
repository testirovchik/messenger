package com.erik.messenger.dto;

import com.erik.messenger.model.MessageType;
import java.time.LocalDateTime;

public class MessageDto {
    private Long id;
    private Long chatId;
    private Long senderId;
    private String content;
    private MessageType type;
    private LocalDateTime createdAt;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}