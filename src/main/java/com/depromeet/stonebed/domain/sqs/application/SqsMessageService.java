package com.depromeet.stonebed.domain.sqs.application;

import com.depromeet.stonebed.domain.fcm.dao.FcmTokenRepository;
import com.depromeet.stonebed.domain.fcm.domain.FcmMessage;
import com.depromeet.stonebed.infra.properties.SqsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
@Transactional
@RequiredArgsConstructor
@Service
public class SqsMessageService {

    private final SqsClient sqsClient;

    private final SqsProperties sqsProperties;

    private final ObjectMapper objectMapper;
    private final FcmTokenRepository fcmTokenRepository;

    public void sendMessage(Object message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            SendMessageRequest sendMsgRequest =
                    SendMessageRequest.builder()
                            .queueUrl(sqsProperties.queueUrl())
                            .messageBody(messageBody)
                            .build();

            SendMessageResponse sendMsgResponse = sqsClient.sendMessage(sendMsgRequest);
            log.info("메시지 전송 완료, ID: {}", sendMsgResponse.messageId());
        } catch (Exception e) {
            log.error("SQS 메시지 전송 실패: {}", e.getMessage());
        }
    }

    public void sendBatchMessages(
            List<String> tokens, String title, String message, String deepLink) {
        // SQS 메시지의 최대 전송 크기는 10개이므로 이를 고려하여 분할합니다.
        int batchSize = 10;
        List<String> failedTokens = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i += batchSize) {
            List<String> batchTokens = tokens.subList(i, Math.min(i + batchSize, tokens.size()));
            List<SendMessageBatchRequestEntry> entries = new ArrayList<>();

            for (String token : batchTokens) {
                try {
                    FcmMessage fcmMessage = FcmMessage.of(title, message, token, deepLink);
                    String messageBody = objectMapper.writeValueAsString(fcmMessage);
                    SendMessageBatchRequestEntry entry =
                            SendMessageBatchRequestEntry.builder()
                                    .id(UUID.randomUUID().toString())
                                    .messageBody(messageBody)
                                    .build();
                    entries.add(entry);
                } catch (Exception e) {
                    log.error("메시지 직렬화 실패: {}", e.getMessage());
                }
            }

            if (!entries.isEmpty()) {
                SendMessageBatchRequest batchRequest =
                        SendMessageBatchRequest.builder()
                                .queueUrl(sqsProperties.queueUrl())
                                .entries(entries)
                                .build();

                try {
                    SendMessageBatchResponse batchResponse =
                            sqsClient.sendMessageBatch(batchRequest);
                    log.info("배치 메시지 전송 응답: {}", batchResponse);
                    // 실패한 메시지 처리
                    List<BatchResultErrorEntry> failedMessages = batchResponse.failed();
                    for (BatchResultErrorEntry failed : failedMessages) {
                        log.error("메시지 전송 실패, ID {}: {}", failed.id(), failed.message());
                        failedTokens.add(failed.id());
                    }

                    // 실패한 토큰 삭제
                    for (String failedToken : failedTokens) {
                        fcmTokenRepository.deleteByToken(failedToken);
                        log.info("비활성화된 FCM 토큰 삭제: {}", failedToken);
                    }

                } catch (Exception e) {
                    log.error("SQS 배치 메시지 전송 실패: {}", e.getMessage());
                }
            }
        }
    }
}
