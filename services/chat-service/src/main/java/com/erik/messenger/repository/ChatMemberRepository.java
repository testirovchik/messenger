package com.erik.messenger.repository;

import com.erik.messenger.model.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

    @Query("SELECT cm1.chatId FROM ChatMember cm1 " +
           "JOIN Chat c ON cm1.chatId = c.id " +
           "JOIN ChatMember cm2 ON cm1.chatId = cm2.chatId " +
           "WHERE cm1.userId = :userA " +
           "AND cm2.userId = :userB " +
           "AND c.type = 'PRIVATE'")
    Optional<Long> findPrivateChatBetweenUsers(@Param("userA") Long userA, @Param("userB") Long userB);
}
