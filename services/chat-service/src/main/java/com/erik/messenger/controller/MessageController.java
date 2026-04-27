package com.erik.messenger.controller;

import com.erik.messenger.dto.MessageDto;
import com.erik.messenger.handler.ChatWebSocketHandler;
import com.erik.messenger.model.Message;
import com.erik.messenger.model.MessageType;
import com.erik.messenger.repository.MessageRepository;
import com.erik.messenger.service.JwtService;
import com.erik.messenger.service.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageRepository messageRepository;
    private final S3Service s3Service;
    private final JwtService jwtService;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public MessageController(MessageRepository messageRepository, S3Service s3Service,
                             JwtService jwtService, ChatWebSocketHandler chatWebSocketHandler) {
        this.messageRepository = messageRepository;
        this.s3Service = s3Service;
        this.jwtService = jwtService;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<List<MessageDto>> getChatHistory(@PathVariable Long chatId) {
        List<Message> messages = messageRepository.findTop30ByChatIdOrderByCreatedAtDesc(chatId);
        Collections.reverse(messages);

        List<MessageDto> dtos = messages.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/upload")
    public ResponseEntity<MessageDto> uploadImage(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("chatId") Long chatId,
            @RequestParam("file") MultipartFile file) throws Exception {

        Long senderId = jwtService.extractUserIdFromToken(authHeader);

        // 1. Upload to S3 and get the object key
        String objectKey = s3Service.uploadImage(file, chatId);

        // 2. Save to Database
        Message message = new Message();
        message.setChatId(chatId);
        message.setSenderId(senderId);
        message.setContent(objectKey); // Store S3 Key, not the bytes!
        message.setType(MessageType.IMAGE);
        message = messageRepository.save(message);

        // 3. Convert to DTO (which generates the Presigned URL)
        MessageDto dto = convertToDto(message);

        // 4. Broadcast via WebSocket so other users see it instantly
        chatWebSocketHandler.broadcastMessage(message, dto);

        return ResponseEntity.ok(dto);
    }

    private MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setChatId(message.getChatId());
        dto.setSenderId(message.getSenderId());
        dto.setType(message.getType());
        dto.setCreatedAt(message.getCreatedAt());

        // The Magic happens here!
        if (message.getType() == MessageType.IMAGE) {
            dto.setContent(s3Service.generatePresignedUrl(message.getContent()));
        } else {
            dto.setContent(message.getContent());
        }
        return dto;
    }
}