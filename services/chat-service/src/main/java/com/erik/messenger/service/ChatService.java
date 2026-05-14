package com.erik.messenger.service;

import com.erik.messenger.model.Chat;
import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.ChatType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.ChatRepository;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final CacheManager cacheManager;

    public ChatService(ChatRepository chatRepository, ChatMemberRepository chatMemberRepository, CacheManager cacheManager) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
        this.cacheManager = cacheManager;
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

        saveMembership(savedChat.getId(), userA, "MEMBER");
        saveMembership(savedChat.getId(), userB, "MEMBER");

        return savedChat;
    }

    @Transactional
    public Chat createGroupChat(Long creatorId, String title) {
        Chat chat = new Chat();
        chat.setType(ChatType.GROUP);
        chat.setChatTitle(title);
        Chat savedChat = chatRepository.save(chat);

        saveMembership(savedChat.getId(), creatorId, "ADMIN");

        return savedChat;
    }

    public void addMeToChat(Long chatId, Long userId, String role) {
        saveMembership(chatId, userId, role);
    }

    private void saveMembership(Long chatId, Long userId, String role) {
        ChatMember member = new ChatMember();
        member.setChatId(chatId);
        member.setUserId(userId);
        member.setRole(role);
        chatMemberRepository.save(member);
        
        // Evict caches AFTER transaction commit to avoid race conditions!
        evictUserCache(userId);
        evictChatCache(chatId);
    }

    private void evictUserCache(Long userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow("userChats", userId);
                }
            });
        } else {
            evictNow("userChats", userId);
        }
    }

    private void evictChatCache(Long chatId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow("chatMembers", chatId);
                }
            });
        } else {
            evictNow("chatMembers", chatId);
        }
    }

    private void evictNow(String cacheName, Object key) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    @Transactional
    public ChatMember addMemberToChat(Long chatId, Long userId, String role, Long requesterId) {
        boolean isAdmin = chatMemberRepository.existsByChatIdAndUserIdAndRole(chatId, requesterId, "ADMIN");
        if (!isAdmin) {
            throw new RuntimeException("Addition denied: Only group admins can add a member");
        }

        ChatMember member = new ChatMember();
        member.setChatId(chatId);
        member.setUserId(userId);
        member.setRole(role != null ? role : "MEMBER");

        ChatMember saved = chatMemberRepository.save(member);
        
        // Evict caches!
        evictUserCache(userId);
        evictChatCache(chatId);
        
        return saved;
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
        if (!exists) {
            throw new RuntimeException("Operation denied: Only group members can get members");
        }
        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
        return members.stream()
                .map(ChatMember::getUserId)
                .collect(Collectors.toList());
    }

    @Transactional
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
        
        // Evict caches!
        evictUserCache(targetUserId);
        evictChatCache(chatId);
    }
}