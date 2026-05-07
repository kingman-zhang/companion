package com.kingman.companion.module.log.repository;

import com.kingman.companion.module.log.entity.DailyLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyLogRepository extends MongoRepository<DailyLog, String> {

    Optional<DailyLog> findByUserIdAndLogDateAndDeletedFalse(String userId, LocalDate logDate);

    List<DailyLog> findTop30ByUserIdAndDeletedFalseOrderByLogDateDesc(String userId);
}
