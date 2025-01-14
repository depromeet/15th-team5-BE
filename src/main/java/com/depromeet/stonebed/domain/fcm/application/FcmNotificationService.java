package com.depromeet.stonebed.domain.fcm.application;

import static com.depromeet.stonebed.global.common.constants.NotificationConstants.*;

import com.depromeet.stonebed.domain.fcm.dao.FcmNotificationRepository;
import com.depromeet.stonebed.domain.fcm.dao.FcmTokenRepository;
import com.depromeet.stonebed.domain.fcm.domain.FcmMessage;
import com.depromeet.stonebed.domain.fcm.domain.FcmNotification;
import com.depromeet.stonebed.domain.fcm.domain.FcmNotificationType;
import com.depromeet.stonebed.domain.fcm.domain.FcmToken;
import com.depromeet.stonebed.domain.fcm.dto.response.FcmNotificationDto;
import com.depromeet.stonebed.domain.fcm.dto.response.FcmNotificationResponse;
import com.depromeet.stonebed.domain.member.dao.MemberRepository;
import com.depromeet.stonebed.domain.member.domain.Member;
import com.depromeet.stonebed.domain.missionRecord.dao.MissionRecordBoostRepository;
import com.depromeet.stonebed.domain.missionRecord.dao.MissionRecordRepository;
import com.depromeet.stonebed.domain.missionRecord.domain.MissionRecord;
import com.depromeet.stonebed.domain.sqs.application.SqsMessageService;
import com.depromeet.stonebed.global.common.constants.FcmNotificationConstants;
import com.depromeet.stonebed.global.error.ErrorCode;
import com.depromeet.stonebed.global.error.exception.CustomException;
import com.depromeet.stonebed.global.util.MemberUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FcmNotificationService {
    private final SqsMessageService sqsMessageService;
    private final FcmNotificationRepository notificationRepository;
    private final MissionRecordBoostRepository missionRecordBoostRepository;
    private final MissionRecordRepository missionRecordRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final MemberRepository memberRepository;
    private final MemberUtil memberUtil;

    public void saveNotification(
            FcmNotificationType type,
            String title,
            String message,
            Long targetId,
            Long memberId,
            Boolean isRead,
            String deepLink) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        FcmNotification notification =
                FcmNotification.createNotification(
                        type, title, message, member, targetId, isRead, deepLink);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public FcmNotificationResponse getNotificationsForCurrentMember(String cursor, int limit) {
        Member member = memberUtil.getCurrentMember();

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<FcmNotification> notifications = getNotifications(cursor, member.getId(), pageable);
        List<FcmNotificationDto> notificationData = convertToNotificationDto(notifications);
        String nextCursor = getNextCursor(notifications);

        return FcmNotificationResponse.from(notificationData, nextCursor);
    }

    private List<FcmNotificationDto> convertToNotificationDto(List<FcmNotification> notifications) {
        List<Long> targetIds = notifications.stream().map(FcmNotification::getTargetId).toList();

        Map<Long, MissionRecord> missionRecordMap =
                missionRecordRepository.findByIdIn(targetIds).stream()
                        .collect(
                                Collectors.toMap(
                                        MissionRecord::getId, missionRecord -> missionRecord));

        return notifications.stream()
                .map(
                        notification -> {
                            MissionRecord missionRecord =
                                    missionRecordMap.get(notification.getTargetId());
                            return FcmNotificationDto.from(notification, missionRecord);
                        })
                .toList();
    }

    private List<FcmNotification> getNotifications(
            String cursor, Long memberId, Pageable pageable) {
        try {
            LocalDateTime cursorDate = null;
            if (cursor != null) cursorDate = LocalDateTime.parse(cursor, DATE_FORMATTER);

            return notificationRepository.findMissionRecordNotificationByMemberPaging(
                    memberId, cursorDate, pageable);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_CURSOR_DATE_FORMAT);
        }
    }

    private String getNextCursor(List<FcmNotification> notifications) {
        if (notifications.isEmpty()) return null;

        FcmNotification lastNotification = notifications.get(notifications.size() - 1);

        boolean hasNext =
                notificationRepository.existsByCreatedAtLessThan(lastNotification.getCreatedAt());

        return hasNext ? lastNotification.getCreatedAt().format(DATE_FORMATTER) : null;
    }

    public void checkAndSendBoostNotification(MissionRecord missionRecord) {
        Long totalBoostCount =
                missionRecordBoostRepository.sumBoostCountByMissionRecord(missionRecord.getId());

        if (totalBoostCount != null) {
            Optional<FcmNotificationConstants> notificationType =
                    determineNotificationType(totalBoostCount);

            long boostCount = determineBoostCount(totalBoostCount);

            notificationType.ifPresent(
                    type -> {
                        if (!notificationAlreadySent(missionRecord, type)) {
                            sendBoostNotification(missionRecord, type, boostCount);
                        }
                    });
        }
    }

    private boolean notificationAlreadySent(
            MissionRecord missionRecord, FcmNotificationConstants notificationConstants) {
        return notificationRepository.existsByTargetIdAndTypeAndTitle(
                missionRecord.getId(),
                FcmNotificationType.BOOSTER,
                notificationConstants.getTitle());
    }

    private Optional<FcmNotificationConstants> determineNotificationType(Long totalBoostCount) {
        if (totalBoostCount >= SUPER_POPULAR_THRESHOLD) {
            return Optional.of(FcmNotificationConstants.SUPER_POPULAR);
        }
        if (totalBoostCount >= POPULAR_THRESHOLD) {
            return Optional.of(FcmNotificationConstants.POPULAR);
        }
        if (totalBoostCount >= FIRST_BOOST_THRESHOLD) {
            return Optional.of(FcmNotificationConstants.FIRST_BOOST);
        }

        return Optional.empty();
    }

    private long determineBoostCount(Long totalBoostCount) {
        if (totalBoostCount >= SUPER_POPULAR_THRESHOLD) {
            return SUPER_POPULAR_THRESHOLD;
        }
        if (totalBoostCount >= POPULAR_THRESHOLD) {
            return POPULAR_THRESHOLD;
        }
        if (totalBoostCount >= FIRST_BOOST_THRESHOLD) {
            return FIRST_BOOST_THRESHOLD;
        }

        return 0;
    }

    private Optional<String> getTokenForMember(Member member) {
        return fcmTokenRepository.findByMember(member).map(FcmToken::getToken);
    }

    private Optional<String> validateTokenForMember(Member member) {
        return getTokenForMember(member).filter(token -> !token.isEmpty());
    }

    private void createAndSendFcmMessage(
            String title, String message, String token, String deepLink) {
        FcmMessage fcmMessage = FcmMessage.of(title, message, token, deepLink);
        sqsMessageService.sendMessage(fcmMessage);
    }

    private void sendBoostNotification(
            MissionRecord missionRecord,
            FcmNotificationConstants notificationConstants,
            long boostCount) {
        String token = validateTokenForMember(missionRecord.getMember()).orElse(null);
        if (token == null) return;

        String deepLink =
                FcmNotification.generateDeepLink(
                        FcmNotificationType.BOOSTER, missionRecord.getId(), boostCount);
        createAndSendFcmMessage(
                notificationConstants.getTitle(),
                notificationConstants.getMessage(),
                token,
                deepLink);

        saveNotification(
                FcmNotificationType.BOOSTER,
                notificationConstants.getTitle(),
                notificationConstants.getMessage(),
                missionRecord.getId(),
                missionRecord.getMember().getId(),
                false,
                deepLink);
    }

    public void markNotificationAsRead(Long notificationId) {
        final Member member = memberUtil.getCurrentMember();
        FcmNotification notification =
                notificationRepository
                        .findByIdAndMember(notificationId, member)
                        .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
        notificationRepository.save(notification);
    }

    private List<FcmNotification> buildNotificationList(
            String title,
            String message,
            List<String> tokens,
            Long targetId,
            FcmNotificationType notificationType,
            String deepLink) {
        List<FcmNotification> notifications = new ArrayList<>();

        for (String token : tokens) {
            Member member =
                    fcmTokenRepository
                            .findByToken(token)
                            .map(FcmToken::getMember)
                            .orElseThrow(
                                    () -> new CustomException(ErrorCode.FAILED_TO_FIND_FCM_TOKEN));

            FcmNotification newNotification =
                    FcmNotification.createNotification(
                            notificationType, title, message, member, targetId, false, deepLink);
            notifications.add(newNotification);
        }

        return notifications;
    }

    public void sendAndNotifications(
            String title,
            String message,
            List<String> tokens,
            Long sourceId,
            Long targetId,
            FcmNotificationType notificationType) {
        List<List<String>> batches = createBatches(tokens);

        String deepLink = FcmNotification.generateDeepLink(notificationType, targetId, null);

        if (notificationType == FcmNotificationType.COMMENT
                || notificationType == FcmNotificationType.RE_COMMENT) {
            deepLink = FcmNotification.generateCommentDeepLink(sourceId, targetId);
        }

        for (List<String> batch : batches) {
            sqsMessageService.sendBatchMessages(batch, title, message, deepLink);
        }

        List<FcmNotification> notifications =
                buildNotificationList(title, message, tokens, targetId, notificationType, deepLink);
        notificationRepository.saveAll(notifications);
    }

    private List<List<String>> createBatches(List<String> tokens) {
        return IntStream.range(
                        0,
                        (tokens.size() + SQS_BATCH_SIZE - 1)
                                / SQS_BATCH_SIZE) // ceil(tokens.size() / SQS_BATCH_SIZE
                .mapToObj(
                        i ->
                                tokens.subList(
                                        i * SQS_BATCH_SIZE,
                                        Math.min(tokens.size(), (i + 1) * SQS_BATCH_SIZE)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> getAllTokens() {
        return fcmTokenRepository.findAllValidTokens();
    }

    @Transactional
    public void invalidateTokenForCurrentMember() {
        Member currentMember = memberUtil.getCurrentMember();
        fcmTokenRepository
                .findByMember(currentMember)
                .ifPresentOrElse(
                        fcmToken -> updateToken(fcmToken, null),
                        () -> {
                            throw new CustomException(ErrorCode.FAILED_TO_FIND_FCM_TOKEN);
                        });
    }

    private void updateToken(FcmToken fcmToken, String token) {
        fcmToken.updateToken(token);
        fcmTokenRepository.save(fcmToken);
    }

    @Transactional
    public void saveFcmToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FCM_TOKEN);
        }

        Member member = memberUtil.getCurrentMember();
        Optional<FcmToken> existingTokenOptional = fcmTokenRepository.findByToken(token);

        existingTokenOptional.ifPresent(
                existingToken -> {
                    if (!existingToken.getMember().equals(member)) {
                        fcmTokenRepository.delete(existingToken);
                    }
                });

        fcmTokenRepository
                .findByMember(member)
                .ifPresentOrElse(
                        fcmToken -> fcmToken.updateToken(token),
                        () -> {
                            FcmToken fcmToken = FcmToken.createFcmToken(member, token);
                            fcmTokenRepository.save(fcmToken);
                        });
    }
}
