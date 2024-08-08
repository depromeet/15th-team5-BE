package com.depromeet.stonebed.domain.fcm.api;

import com.depromeet.stonebed.domain.fcm.application.FcmService;
import com.depromeet.stonebed.domain.fcm.application.FcmTokenService;
import com.depromeet.stonebed.domain.fcm.dto.request.FcmSendRequest;
import com.depromeet.stonebed.domain.fcm.dto.request.FcmTokenRequest;
import com.depromeet.stonebed.global.util.FcmNotificationUtil;
import com.google.firebase.messaging.Notification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. [알림]", description = "알림 관련 API입니다.")
@RestController
@RequestMapping("/alarm")
@RequiredArgsConstructor
public class FcmController {
    private final FcmService fcmService;
    private final FcmTokenService fcmTokenService;

    @Operation(summary = "푸시 메시지 전송", description = "저장된 모든 토큰에 푸시 메시지를 전송합니다.")
    @PostMapping("/send")
    public ResponseEntity<Void> pushMessageToAll(
            @RequestBody @Validated FcmSendRequest fcmSendRequest) {
        Notification notification =
                FcmNotificationUtil.buildNotification(
                        fcmSendRequest.title(), fcmSendRequest.body());
        fcmService.sendMulticastMessageToAll(notification);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "FCM 토큰 저장", description = "로그인 시 FCM 토큰을 저장합니다.")
    @PostMapping("/token")
    public ResponseEntity<Void> storeToken(
            @RequestBody @Validated FcmTokenRequest fcmTokenRequest) {
        fcmTokenService.storeOrUpdateToken(fcmTokenRequest.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "FCM 토큰 삭제", description = "로그아웃 시 FCM 토큰을 삭제합니다.")
    @DeleteMapping("/token")
    public ResponseEntity<Void> deleteToken(
            @RequestBody @Validated FcmTokenRequest fcmTokenRequest) {
        fcmTokenService.invalidateToken(fcmTokenRequest.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "앱 실행 시 FCM 토큰 타임스탬프 갱신", description = "앱 실행 시 FCM 토큰의 타임스탬프를 갱신합니다.")
    @PostMapping("/token/refresh")
    public ResponseEntity<Void> refreshTokenTimestamp() {
        fcmTokenService.refreshTokenTimestampForCurrentUser();
        return ResponseEntity.ok().build();
    }
}