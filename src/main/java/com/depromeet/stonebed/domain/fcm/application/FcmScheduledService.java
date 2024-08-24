package com.depromeet.stonebed.domain.fcm.application;

import com.depromeet.stonebed.domain.fcm.dao.FcmRepository;
import com.depromeet.stonebed.domain.fcm.domain.FcmToken;
import com.depromeet.stonebed.domain.member.domain.MemberStatus;
import com.depromeet.stonebed.domain.missionRecord.dao.MissionRecordRepository;
import com.depromeet.stonebed.domain.missionRecord.domain.MissionRecordStatus;
import com.depromeet.stonebed.global.common.constants.FcmNotificationConstants;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmScheduledService {
    private final FcmTokenService fcmTokenService;
    private final FcmNotificationService fcmNotificationService;
    private final FcmRepository fcmRepository;
    private final MissionRecordRepository missionRecordRepository;

    // 매일 0시 0분에 실행
    @Scheduled(cron = "0 0 0 * * ?")
    public void removeInactiveTokens() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(2);
        List<FcmToken> inactiveTokens = fcmRepository.findAllByUpdatedAtBefore(cutoffDate);
        fcmRepository.deleteAll(inactiveTokens);
        log.info("비활성 토큰 {}개 삭제 완료", inactiveTokens.size());
    }

    // 매일 9시 0분에 실행
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendDailyNotification() {
        FcmNotificationConstants notificationConstants = FcmNotificationConstants.MISSION_START;
        String title = notificationConstants.getTitle();
        String message = notificationConstants.getMessage();
        List<String> tokens = fcmTokenService.getAllTokens();

        fcmNotificationService.sendAndNotifications(title, message, tokens, true);

        log.info("모든 사용자에게 정규 알림 전송 및 저장 완료");
    }

    // 매일 19시 0분에 실행
    @Scheduled(cron = "0 0 19 * * ?")
    public void sendReminderToIncompleteMissions() {
        FcmNotificationConstants notificationConstants = FcmNotificationConstants.MISSION_REMINDER;
        String title = notificationConstants.getTitle();
        String message = notificationConstants.getMessage();

        List<String> tokens = getIncompleteMissionTokens();
        fcmNotificationService.sendAndNotifications(title, message, tokens, false);

        log.info("미완료 미션 사용자에게 리마인더 전송 및 저장 완료. 총 토큰 수: {}", tokens.size());
    }

    private List<String> getIncompleteMissionTokens() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return missionRecordRepository
                .findAllByCreatedAtBetweenAndStatusNot(
                        startOfDay, endOfDay, MissionRecordStatus.COMPLETED)
                .stream()
                .map(
                        missionRecord -> {
                            FcmToken fcmToken =
                                    fcmRepository
                                            .findByMemberAndMemberStatus(
                                                    missionRecord.getMember(), MemberStatus.NORMAL)
                                            .orElse(null);
                            return fcmToken != null ? fcmToken.getToken() : null;
                        })
                .filter(Objects::nonNull)
                .toList();
    }
}
