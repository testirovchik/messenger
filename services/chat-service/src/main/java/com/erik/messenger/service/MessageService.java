package com.erik.messenger.service;

import com.erik.messenger.dto.MessageDto;
import com.erik.messenger.exception.NotFoundException;
import com.erik.messenger.handler.ChatWebSocketHandler;
import com.erik.messenger.model.Message;
import com.erik.messenger.model.MessageType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final S3Service s3Service;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatMemberRepository chatMemberRepository;

    public MessageService(MessageRepository messageRepository, S3Service s3Service,
                          ChatWebSocketHandler chatWebSocketHandler,
                          ChatMemberRepository chatMemberRepository) {
        this.messageRepository = messageRepository;
        this.s3Service = s3Service;
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.chatMemberRepository = chatMemberRepository;
    }

    public Page<MessageDto> getChatHistory(Long chatId, Long requesterId, Pageable pageable) {
        boolean exists = chatMemberRepository.existsByChatIdAndUserId(chatId, requesterId);
        if (!exists) {
            throw new RuntimeException("Operation denied: Only group members can get chat messages");
        }

        // Fetch the specific page of messages
        Page<Message> messagePage = messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable);

        // Convert the Page of Entities into a Page of DTOs
        return messagePage.map(this::convertToDto);
    }

    @Transactional
    public MessageDto uploadImage(Long chatId, Long senderId, MultipartFile file) throws Exception {
        String objectKey = s3Service.uploadImage(file, chatId);

        Message message = new Message();
        message.setChatId(chatId);
        message.setSenderId(senderId);
        message.setContent(objectKey);
        message.setType(MessageType.IMAGE);
        message = messageRepository.save(message);

        MessageDto dto = convertToDto(message);

        chatWebSocketHandler.broadcastMessage(message, dto);

        return dto;
    }

    private MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setChatId(message.getChatId());
        dto.setSenderId(message.getSenderId());
        dto.setType(message.getType());
        dto.setCreatedAt(message.getCreatedAt());

        if (message.getType() == MessageType.IMAGE) {
            dto.setContent(s3Service.generatePresignedUrl(message.getContent()));
        } else {
            dto.setContent(message.getContent());
        }
        return dto;
    }

    @Transactional
    public void deleteMessage(Long messageId, Long requesterId){
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSenderId().equals(requesterId)) {
            throw new RuntimeException("Operation denied: You can only delete your own messages");
        }

        message.setDeleted(true);
        message.setContent("This message was deleted"); // Hide the original text/image URL
        messageRepository.save(message);
        chatWebSocketHandler.broadcastMessageDeletion(message.getChatId(), messageId);
    }

    public void editMessage(Long messageId, String newContent, Long requesterId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if(!message.getSenderId().equals(requesterId)) {
            throw new RuntimeException("Operation denied: You can only edit your own messages");
        }
        message.setContent(newContent);
        message.setEdited(true);
        messageRepository.save(message);
        chatWebSocketHandler.broadcastMessageEdit(message.getChatId(), messageId, newContent);
    }
}