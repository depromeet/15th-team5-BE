package com.depromeet.stonebed.domain.fcm.application;

import com.depromeet.stonebed.domain.fcm.dao.FcmRepository;
import com.depromeet.stonebed.domain.fcm.domain.FcmResponseErrorType;
import com.depromeet.stonebed.domain.fcm.domain.FcmToken;
import com.depromeet.stonebed.domain.fcm.dto.request.FcmMessageRequest;
import com.depromeet.stonebed.domain.member.domain.Member;
import com.depromeet.stonebed.global.error.ErrorCode;
import com.depromeet.stonebed.global.error.exception.CustomException;
import com.depromeet.stonebed.global.util.MemberUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {
    private static final String FIREBASE_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String FCM_API_URL =
            "https://fcm.googleapis.com/v1/projects/walwal-dev-fad47/messages:send";

    private final FcmRepository fcmRepository;
    private final MemberUtil memberUtil;

    @Transactional(readOnly = true)
    public void sendMessageToAll(String title, String body) {
        List<String> tokens = getAllTokens();
        for (String token : tokens) {
            try {
                sendMessageTo(token, title, body);
            } catch (IOException e) {
                log.error("다음 token이 FCM 메세지 전송에 실패했습니다: {}", token, e);
            }
        }
    }

    @Transactional
    public void sendMessageTo(String token, String title, String body) throws IOException {
        String message = createFcmMessage(token, title, body);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate
                .getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        HttpHeaders headers = createFcmHeaders();
        HttpEntity<String> entity = new HttpEntity<>(message, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(FCM_API_URL, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            String responseBody = response.getBody();
            if (FcmResponseErrorType.contains(responseBody, FcmResponseErrorType.NOT_REGISTERED)
                    || FcmResponseErrorType.contains(
                            responseBody, FcmResponseErrorType.INVALID_REGISTRATION)) {
                deleteTokenByToken(token);
            }
            throw new CustomException(ErrorCode.FAILED_TO_SEND_FCM_MESSAGE);
        }
    }

    private HttpHeaders createFcmHeaders() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + getAccessToken());
        return headers;
    }

    private String getAccessToken() throws IOException {
        String firebaseCredentials = System.getenv("FIREBASE_CONFIG");

        if (firebaseCredentials == null || firebaseCredentials.isEmpty()) {
            log.error("FIREBASE_CONFIG 환경 변수가 설정되지 않았습니다.");
            throw new CustomException(ErrorCode.FIREBASE_CONFIG_NOT_FOUND);
        }

        GoogleCredentials googleCredentials =
                GoogleCredentials.fromStream(
                                new ByteArrayInputStream(
                                        firebaseCredentials.getBytes(StandardCharsets.UTF_8)))
                        .createScoped(List.of(FIREBASE_SCOPE));

        googleCredentials.refreshIfExpired();
        return googleCredentials.getAccessToken().getTokenValue();
    }

    private String createFcmMessage(String token, String title, String body)
            throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        FcmMessageRequest fcmMessageRequest =
                FcmMessageRequest.builder()
                        .message(
                                FcmMessageRequest.Message.builder()
                                        .token(token)
                                        .notification(
                                                FcmMessageRequest.Notification.builder()
                                                        .title(title)
                                                        .body(body)
                                                        .image(null)
                                                        .build())
                                        .build())
                        .validateOnly(false)
                        .build();

        return om.writeValueAsString(fcmMessageRequest);
    }

    @Transactional
    public void storeOrUpdateToken(String token) {
        final Member member = memberUtil.getCurrentMember();
        Optional<FcmToken> existingToken = fcmRepository.findByMember(member);
        existingToken.ifPresentOrElse(
                fcmToken -> {
                    fcmToken.updateToken(token);
                    fcmRepository.save(fcmToken);
                },
                () -> {
                    FcmToken fcmToken = new FcmToken(member, token);
                    fcmRepository.save(fcmToken);
                });
    }

    @Transactional
    public void refreshTokenTimestampForCurrentUser() {
        final Member member = memberUtil.getCurrentMember();
        Optional<FcmToken> existingToken = fcmRepository.findByMember(member);
        existingToken.ifPresentOrElse(
                fcmToken -> {
                    fcmToken.updateToken(fcmToken.getToken());
                    fcmRepository.save(fcmToken);
                },
                () -> {
                    throw new CustomException(ErrorCode.FAILED_TO_FIND_FCM_TOKEN);
                });
    }

    @Transactional
    public void deleteToken() {
        final Member member = memberUtil.getCurrentMember();
        fcmRepository.deleteByMember(member);
    }

    @Transactional
    public void deleteTokenByToken(String token) {
        fcmRepository.deleteByToken(token);
    }

    @Transactional(readOnly = true)
    public List<String> getAllTokens() {
        return fcmRepository.findAll().stream().map(FcmToken::getToken).toList();
    }
}
