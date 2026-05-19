package com.kingman.companion.module.log.repository;

import com.kingman.companion.module.log.entity.UserFeedback;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserFeedbackRepository extends MongoRepository<UserFeedback, String> {
}
