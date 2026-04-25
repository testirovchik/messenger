package com.erik.messenger.controller;

import com.erik.messenger.model.Chat;
import com.erik.messenger.service.ChatService;
import com.erik.messenger.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final JwtService jwtService;
    private final ChatService chatService;

    public ChatController(JwtService jwtService, ChatService chatService) {
        this.jwtService = jwtService;
        this.chatService = chatService;
    }

    @PostMapping("/private")
    public ResponseEntity<Chat> createPrivateChat(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long partnerId) {

        Long myId = jwtService.extractUserIdFromToken(authHeader);
        Chat chat = chatService.getOrCreatePrivateChat(myId, partnerId);

        return ResponseEntity.ok(chat);
    }
}
