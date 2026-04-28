package com.erik.messenger.service;

import com.erik.messenger.model.Chat;
import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.ChatType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.ChatRepository;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;

    public ChatService(ChatRepository chatRepository, ChatMemberRepository chatMemberRepository) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    @Transactional
    public Chat getOrCreatePrivateChat(Long myId, Long partnerId) {
        return chatMemberRepository.findPrivateChatBetweenUsers(myId, partnerId)
                .flatMap(chatRepository::findById)
                .orElseGet(() -> createPrivateChat(myId, partnerId));
    }

    private Chat createPrivateChat(Long userA, Long userB) {
        Chat chat = new Chat();
        chat.setType(ChatType.PRIVATE);
        Chat savedChat = chatRepository.save(chat);

        addMemberToChat(savedChat.getId(), userA, "MEMBER");
        addMemberToChat(savedChat.getId(), userB, "MEMBER");

        return savedChat;
    }

    @Transactional
    public Chat createGroupChat(Long creatorId, String title) {
        Chat chat = new Chat();
        chat.setType(ChatType.GROUP);
        chat.setChatTitle(title);
        Chat savedChat = chatRepository.save(chat);

        addMemberToChat(savedChat.getId(), creatorId, "ADMIN");

        return savedChat;
    }

    @Transactional
    public ChatMember addMemberToChat(Long chatId, Long userId, String role) {
        ChatMember member = new ChatMember();
        member.setChatId(chatId);
        member.setUserId(userId);
        member.setRole(role != null ? role : "MEMBER");
        return chatMemberRepository.save(member);
    }

    public List<Chat> getUserChats(Long userId) {
        List<ChatMember> memberships = chatMemberRepository.findByUserId(userId);
        List<Long> chatIds = memberships.stream()
                .map(ChatMember::getChatId)
                .collect(Collectors.toList());
        return chatRepository.findAllById(chatIds);
    }

    public List<Long> getChatMembers(Long chatId) {
        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
        List<Long> userIds = members.stream()
                .map(ChatMember::getUserId)
                .collect(Collectors.toList());
        return userIds;
    }
}
