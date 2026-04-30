package com.kingman.companion.module.rewrite.repository;

import com.kingman.companion.module.rewrite.entity.RewriteDailyUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RewriteDailyUsageRepository extends MongoRepository<RewriteDailyUsage, String> {

    Optional<RewriteDailyUsage> findByDeviceIdAndUsageDate(String deviceId, LocalDate usageDate);
}
