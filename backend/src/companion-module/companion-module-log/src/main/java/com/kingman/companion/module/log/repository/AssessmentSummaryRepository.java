package com.kingman.companion.module.log.repository;

import com.kingman.companion.module.log.entity.AssessmentSummary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessmentSummaryRepository extends MongoRepository<AssessmentSummary, String> {

    /** 取用户最近一次评估（按创建时间倒序） */
    Optional<AssessmentSummary> findFirstByUserIdOrderByIdDesc(String userId);
}
