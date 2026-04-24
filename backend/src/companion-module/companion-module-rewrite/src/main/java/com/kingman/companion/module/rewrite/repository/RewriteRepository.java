package com.kingman.companion.module.rewrite.repository;

import com.kingman.companion.module.rewrite.entity.RewriteRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RewriteRepository extends MongoRepository<RewriteRecord, String> {

    long countByUserIdAndCreateTimeAfterAndDeletedFalse(String userId, LocalDateTime after);
}
