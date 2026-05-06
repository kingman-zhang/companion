package com.kingman.companion.module.chat.repository;

import com.kingman.companion.module.chat.entity.ChatMessage;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findTop10BySessionIdAndDeletedFalse(String sessionId, Sort sort);

    List<ChatMessage> findBySessionIdAndDeletedFalseOrderByCreateTimeAsc(String sessionId);
}
