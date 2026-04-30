package com.erik.messenger.service;

import com.erik.messenger.model.Chat;
import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.ChatType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.ChatRepository;
import org.apache.catalina.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

        addMeToChat(savedChat.getId(), userA, "MEMBER");
        addMeToChat(savedChat.getId(), userB, "MEMBER");

        return savedChat;
    }

    @Transactional
    public Chat createGroupChat(Long creatorId, String title) {
        Chat chat = new Chat();
        chat.setType(ChatType.GROUP);
        chat.setChatTitle(title);
        Chat savedChat = chatRepository.save(chat);
        addMeToChat(savedChat.getId(), creatorId, "ADMIN");
        return savedChat;
    }

    @CacheEvict(value = "userChats", key = "#userId")
    public void addMeToChat(Long chatId, Long userId, String role) {
        ChatMember member = new ChatMember();
        member.setChatId(chatId);
        member.setUserId(userId);
        member.setRole(role);
        chatMemberRepository.save(member);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "chatMembers", key = "#chatId"), // clears the group's member list
            @CacheEvict(value = "userChats", key = "#userId")    // clears the new user's chat list
    })
    public ChatMember addMemberToChat(Long chatId, Long userId, String role, Long requesterId) {
        boolean isAdmin = chatMemberRepository.existsByChatIdAndUserIdAndRole(chatId, requesterId, "ADMIN");
        if(!isAdmin) {
            throw new RuntimeException("Addition denied: Only group admins can add a member");
        }

        ChatMember member = new ChatMember();
        member.setChatId(chatId);
        member.setUserId(userId);
        member.setRole(role != null ? role : "MEMBER");

        return chatMemberRepository.save(member);
    }

    @Cacheable(value = "userChats", key = "#userId")
    public List<Chat> getUserChats(Long userId) {
        List<ChatMember> memberships = chatMemberRepository.findByUserId(userId);
        List<Long> chatIds = memberships.stream()
                .map(ChatMember::getChatId)
                .collect(Collectors.toList());
        return chatRepository.findAllById(chatIds);
    }

    @Cacheable(value = "chatMembers", key = "#chatId")
    public List<Long> getChatMembers(Long chatId, Long requesterId) {
        boolean exists = chatMemberRepository.existsByChatIdAndUserId(chatId, requesterId);
        if(!exists) {
            throw new RuntimeException("Operation denied: Only group members can get members");
        }
        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
        List<Long> userIds = members.stream()
                .map(ChatMember::getUserId)
                .collect(Collectors.toList());
        return userIds;
    }

    public void removeMemberFromChat(Long chatId, Long targetUserId, Long requesterId) {

        ChatMember targetMember = chatMemberRepository.findByChatIdAndUserId(chatId, targetUserId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this chat"));

        if (!targetUserId.equals(requesterId)) {
            boolean isAdmin = chatMemberRepository.existsByChatIdAndUserIdAndRole(chatId, requesterId, "ADMIN");
            if (!isAdmin) {
                throw new RuntimeException("Operation denied: Only group admins can kick other members");
            }
        }
        chatMemberRepository.delete(targetMember);
    }
}
