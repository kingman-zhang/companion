package com.kingman.companion.module.chat.repository;

import com.kingman.companion.module.chat.entity.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {

    Optional<ChatSession> findByIdAndDeletedFalse(String id);

    List<ChatSession> findTop20ByUserIdAndDeletedFalseOrderByCreateTimeDesc(String userId);
}
