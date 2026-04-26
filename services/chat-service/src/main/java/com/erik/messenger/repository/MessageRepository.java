package com.erik.messenger.repository;

import com.erik.messenger.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findTop30ByChatIdOrderByCreatedAtDesc(Long chatId);
}
