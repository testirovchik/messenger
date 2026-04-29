package com.erik.messenger.controller;

import com.erik.messenger.model.Chat;
import com.erik.messenger.model.ChatMember;
import com.erik.messenger.service.ChatService;
import com.erik.messenger.service.JwtService;
import org.apache.catalina.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final JwtService jwtService;
    private final ChatService chatService;

    public ChatController(JwtService jwtService, ChatService chatService) {
        this.jwtService = jwtService;
        this.chatService = chatService;
    }

    @GetMapping("/my-chats")
    public ResponseEntity<List<Chat>> getMyChats(@RequestHeader("Authorization") String authHeader) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);
        List<Chat> chats = chatService.getUserChats(myId);
        return ResponseEntity.ok(chats);
    }

    @PostMapping("/private")
    public ResponseEntity<Chat> createPrivateChat(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long partnerId) {

        Long myId = jwtService.extractUserIdFromToken(authHeader);
        Chat chat = chatService.getOrCreatePrivateChat(myId, partnerId);

        return ResponseEntity.ok(chat);
    }

    @PostMapping("/group")
    public ResponseEntity<Chat> createGroupChat(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String title) {

        Long myId = jwtService.extractUserIdFromToken(authHeader);
        Chat chat = chatService.createGroupChat(myId, title);

        return ResponseEntity.ok(chat);
    }

    @PostMapping("/{chatId}/members")
    public ResponseEntity<ChatMember> addMember(
            @PathVariable Long chatId,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "MEMBER") String role) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);
        ChatMember member = chatService.addMemberToChat(chatId, userId, role, myId);
        return ResponseEntity.ok(member);
    }

    @GetMapping("/{chatId}/members")
    public ResponseEntity<List<Long>> getMembers(
            @PathVariable Long chatId,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);
        List<Long> userIds = chatService.getChatMembers(chatId, myId);
        return ResponseEntity.ok(userIds);
    }

    @DeleteMapping("/{chatId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long chatId,
            @PathVariable Long userId,
            @RequestHeader("Authorization") String authHeader) {

        Long requesterId = jwtService.extractUserIdFromToken(authHeader);

        chatService.removeMemberFromChat(chatId, userId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
