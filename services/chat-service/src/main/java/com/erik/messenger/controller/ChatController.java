package com.erik.messenger.controller;

import com.erik.messenger.model.Chat;
import com.erik.messenger.model.ChatMember;
import com.erik.messenger.service.ChatService;
import com.erik.messenger.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@Tag(name = "Chats", description = "Endpoints for creating and managing private and group chat rooms")
public class ChatController {

    private final JwtService jwtService;
    private final ChatService chatService;

    public ChatController(JwtService jwtService, ChatService chatService) {
        this.jwtService = jwtService;
        this.chatService = chatService;
    }

    @Operation(summary = "Get My Chats", description = "Retrieves a list of all chat rooms the authenticated user is currently a member of.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user's chats")

    @GetMapping("/my-chats")
    public ResponseEntity<List<Chat>> getMyChats(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);
        List<Chat> chats = chatService.getUserChats(myId);
        return ResponseEntity.ok(chats);
    }

    @Operation(summary = "Create/Get Private Chat", description = "Creates a new private chat with another user, or returns the existing private chat if they already have one.")
    @ApiResponse(responseCode = "200", description = "Successfully created or fetched private chat")

    @PostMapping("/private")
    public ResponseEntity<Chat> createPrivateChat(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "The User ID of the person to chat with", example = "2") @RequestParam Long partnerId) {

        Long myId = jwtService.extractUserIdFromToken(authHeader);
        Chat chat = chatService.getOrCreatePrivateChat(myId, partnerId);

        return ResponseEntity.ok(chat);
    }

    @Operation(summary = "Create Group Chat", description = "Creates a new group chat and automatically assigns the creator as the ADMIN.")
    @ApiResponse(responseCode = "200", description = "Successfully created group chat")

    @PostMapping("/group")
    public ResponseEntity<Chat> createGroupChat(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "The title of the new group chat") @RequestParam String title) {

        Long myId = jwtService.extractUserIdFromToken(authHeader);
        Chat chat = chatService.createGroupChat(myId, title);

        return ResponseEntity.ok(chat);
    }

    @Operation(summary = "Add Member to Chat", description = "Adds a user to a specific chat room. The requester must be an ADMIN of the group to perform this action.")
    @ApiResponse(responseCode = "200", description = "Successfully added the user to the chat")

    @PostMapping("/{chatId}/members")
    public ResponseEntity<ChatMember> addMember(
            @Parameter(description = "ID of the chat room", example = "1") @PathVariable Long chatId,
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "User ID of the person being added", example = "3") @RequestParam Long userId,
            @Parameter(description = "Role of the new member (MEMBER or ADMIN)", example = "MEMBER") @RequestParam(required = false, defaultValue = "MEMBER") String role) {

        Long myId = jwtService.extractUserIdFromToken(authHeader);
        ChatMember member = chatService.addMemberToChat(chatId, userId, role, myId);
        return ResponseEntity.ok(member);
    }

    @Operation(summary = "Get Chat Members", description = "Retrieves a list of User IDs for everyone currently in the specified chat room.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved chat members")
    @GetMapping("/{chatId}/members")
    public ResponseEntity<List<Long>> getMembers(
            @Parameter(description = "ID of the chat room", example = "1") @PathVariable Long chatId,
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader
    ) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);
        List<Long> userIds = chatService.getChatMembers(chatId, myId);
        return ResponseEntity.ok(userIds);
    }

    @Operation(summary = "Remove Member from Chat", description = "Removes a user from the chat. A user can remove themselves, or an ADMIN can kick another user.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User successfully removed"),
            @ApiResponse(responseCode = "403", description = "Requester does not have permission to kick this user")
    })
    @DeleteMapping("/{chatId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @Parameter(description = "ID of the chat room", example = "1") @PathVariable Long chatId,
            @Parameter(description = "User ID to be removed", example = "3") @PathVariable Long userId,
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {

        Long requesterId = jwtService.extractUserIdFromToken(authHeader);

        chatService.removeMemberFromChat(chatId, userId, requesterId);
        return ResponseEntity.noContent().build();
    }
}