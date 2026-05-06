package com.kingman.companion.module.rewrite.repository;

import com.kingman.companion.module.rewrite.entity.RewriteRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RewriteRepository extends MongoRepository<RewriteRecord, String> {

    long countByUserIdAndCreateTimeAfterAndDeletedFalse(String userId, LocalDateTime after);

    List<RewriteRecord> findTop20ByUserIdAndDeletedFalseOrderByCreateTimeDesc(String userId);
}
