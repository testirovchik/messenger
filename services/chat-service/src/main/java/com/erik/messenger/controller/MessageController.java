package com.erik.messenger.controller;

import com.erik.messenger.model.Message;
import com.erik.messenger.repository.MessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageRepository messageRepository;

    public MessageController(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<List<Message>> getChatHistory(@PathVariable Long chatId) {
        List<Message> messages = messageRepository.findTop30ByChatIdOrderByCreatedAtDesc(chatId);
        Collections.reverse(messages);
        return ResponseEntity.ok(messages);
    }
}
