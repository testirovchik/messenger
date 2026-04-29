package com.erik.messenger.service;

import com.erik.messenger.dto.MessageDto;
import com.erik.messenger.handler.ChatWebSocketHandler;
import com.erik.messenger.model.Message;
import com.erik.messenger.model.MessageType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.MessageRepository;
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

    public List<MessageDto> getChatHistory(Long chatId, Long requesterId, Long cursorId) {
        boolean exists = chatMemberRepository.existsByChatIdAndUserId(chatId, requesterId);
        if (!exists) {
            throw new RuntimeException("Operation denied: Only group members can get chat messages");
        }

        List<Message> messages;
        if(cursorId == null) {
            messages = messageRepository.findTop30ByChatIdOrderByCreatedAtDesc(chatId);
        }
        else {
            messages = messageRepository.getOlderMessages(chatId, cursorId);
        }
        Collections.reverse(messages);

        return messages.stream().map(this::convertToDto).collect(Collectors.toList());
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
}