package com.erik.messenger.controller;

import com.erik.messenger.dto.MessageDto;
import com.erik.messenger.service.JwtService;
import com.erik.messenger.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messages", description = "Endpoints for managing, fetching, and modifying chat messages")
public class MessageController {

    private final JwtService jwtService;
    private final MessageService messageService;

    public MessageController(JwtService jwtService, MessageService messageService) {
        this.jwtService = jwtService;
        this.messageService = messageService;
    }

    @Operation(summary = "Get Chat History", description = "Retrieves a paginated list of messages for a specific chat room, ordered from newest to oldest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved chat history"),
            @ApiResponse(responseCode = "403", description = "User is not a member of this chat")
    })
    @GetMapping("/{chatId}")
    public ResponseEntity<Page<MessageDto>> getChatHistory(
            @Parameter(description = "ID of the chat room", example = "1") @PathVariable Long chatId,
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Page number (0-indexed)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of messages per page", example = "30") @RequestParam(defaultValue = "30") int size
    ) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);

        Pageable pageable = PageRequest.of(page, size);

        Page<MessageDto> dtos = messageService.getChatHistory(chatId, myId, pageable);
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Upload Image Message", description = "Uploads an image file to AWS S3 and sends it as a new message to the chat room.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image successfully uploaded and message sent"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or upload failed")
    })
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<MessageDto> uploadImage(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "ID of the chat room", example = "1") @RequestParam("chatId") Long chatId,
            @Parameter(description = "The image file to upload") @RequestParam("file") MultipartFile file) throws Exception {

        Long senderId = jwtService.extractUserIdFromToken(authHeader);
        MessageDto dto = messageService.uploadImage(chatId, senderId, file);

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Delete Message", description = "Soft-deletes a message. Only the original sender can delete their message.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Message successfully deleted"),
            @ApiResponse(responseCode = "403", description = "User is not the sender of the message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @Parameter(description = "ID of the message to delete", example = "100") @PathVariable Long messageId,
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) throws Exception {

        Long myId = jwtService.extractUserIdFromToken(authHeader);
        messageService.deleteMessage(messageId, myId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Edit Message", description = "Modifies the text content of an existing message. Only the original sender can edit their message.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message successfully edited"),
            @ApiResponse(responseCode = "403", description = "User is not the sender of the message")
    })
    @PatchMapping("/{messageId}")
    public ResponseEntity<Void> editMessage(
            @Parameter(description = "ID of the message to edit", example = "100") @PathVariable Long messageId,
            @Parameter(description = "The new text content for the message") @RequestParam String newContent,
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader
    ) {
        Long myId = jwtService.extractUserIdFromToken(authHeader);
        messageService.editMessage(messageId, newContent, myId);
        return ResponseEntity.ok().build();
    }
}