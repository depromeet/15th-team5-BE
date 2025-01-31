package com.depromeet.stonebed.domain.missionRecord.dao;

import com.depromeet.stonebed.domain.missionRecord.domain.MissionRecordBoost;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionRecordBoostRepository extends JpaRepository<MissionRecordBoost, Long> {
    @Query(
            "SELECT SUM(mrb.count) FROM MissionRecordBoost mrb WHERE mrb.missionRecord.id = :missionRecordId")
    Long sumBoostCountByMissionRecord(@Param("missionRecordId") Long missionRecordId);

    @Modifying
    @Query("DELETE FROM MissionRecordBoost mrb WHERE mrb.missionRecord.id in (:missionRecordIds)")
    void deleteAllByRecordIds(@Param("missionRecordIds") List<Long> missionRecordIds);
}
