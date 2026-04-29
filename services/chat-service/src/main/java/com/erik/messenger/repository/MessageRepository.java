package com.erik.messenger.repository;

import com.erik.messenger.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop30ByChatIdOrderByCreatedAtDesc(Long chatId);
    @Query(value = "SELECT * FROM messages WHERE chat_id = :chatId AND id < :cursorId ORDER BY id DESC LIMIT 30", nativeQuery = true)
    List<Message> getOlderMessages(@Param("chatId") Long chatId, @Param("cursorId") Long cursorId);
}
