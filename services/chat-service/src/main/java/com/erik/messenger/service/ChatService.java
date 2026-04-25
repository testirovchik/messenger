package com.erik.messenger.service;

import com.erik.messenger.model.Chat;
import com.erik.messenger.model.ChatMember;
import com.erik.messenger.model.ChatType;
import com.erik.messenger.repository.ChatMemberRepository;
import com.erik.messenger.repository.ChatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        ChatMember memberA = new ChatMember();
        memberA.setChatId(savedChat.getId());
        memberA.setUserId(userA);
        memberA.setRole("MEMBER");
        
        ChatMember memberB = new ChatMember();
        memberB.setChatId(savedChat.getId());
        memberB.setUserId(userB);
        memberB.setRole("MEMBER");

        chatMemberRepository.save(memberA);
        chatMemberRepository.save(memberB);

        return savedChat;
    }
}
