package com.erik.messenger.controller;

import com.erik.messenger.dto.MessageDto;
import com.erik.messenger.service.JwtService;
import com.erik.messenger.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final JwtService jwtService;
    private final MessageService messageService;

    public MessageController(JwtService jwtService, MessageService messageService) {
        this.jwtService = jwtService;
        this.messageService = messageService;
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<List<MessageDto>> getChatHistory(
            @PathVariable Long chatId,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long cursorId
    ) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);
        List<MessageDto> dtos = messageService.getChatHistory(chatId, myId, cursorId);
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/upload")
    public ResponseEntity<MessageDto> uploadImage(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("chatId") Long chatId,
            @RequestParam("file") MultipartFile file) throws Exception {

        Long senderId = jwtService.extractUserIdFromToken(authHeader);

        MessageDto dto = messageService.uploadImage(chatId, senderId, file);

        return ResponseEntity.ok(dto);
    }
}