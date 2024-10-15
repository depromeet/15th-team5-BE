package com.depromeet.stonebed.domain.comment.application;

import com.depromeet.stonebed.domain.comment.dao.CommentRepository;
import com.depromeet.stonebed.domain.comment.domain.Comment;
import com.depromeet.stonebed.domain.comment.dto.request.CommentCreateRequest;
import com.depromeet.stonebed.domain.comment.dto.response.CommentCreateResponse;
import com.depromeet.stonebed.domain.comment.dto.response.CommentFindOneResponse;
import com.depromeet.stonebed.domain.comment.dto.response.CommentFindResponse;
import com.depromeet.stonebed.domain.fcm.application.FcmNotificationService;
import com.depromeet.stonebed.domain.fcm.dao.FcmTokenRepository;
import com.depromeet.stonebed.domain.fcm.domain.FcmNotificationType;
import com.depromeet.stonebed.domain.fcm.domain.FcmToken;
import com.depromeet.stonebed.domain.member.domain.Member;
import com.depromeet.stonebed.domain.missionRecord.dao.MissionRecordRepository;
import com.depromeet.stonebed.domain.missionRecord.domain.MissionRecord;
import com.depromeet.stonebed.global.common.constants.FcmNotificationConstants;
import com.depromeet.stonebed.global.error.ErrorCode;
import com.depromeet.stonebed.global.error.exception.CustomException;
import com.depromeet.stonebed.global.util.MemberUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final MemberUtil memberUtil;
    private final CommentRepository commentRepository;
    private final MissionRecordRepository missionRecordRepository;
    private final FcmNotificationService fcmNotificationService;
    private final FcmTokenRepository fcmTokenRepository;
    private static final Long ROOT_COMMENT_PARENT_ID = -1L;

    public CommentCreateResponse createComment(CommentCreateRequest request) {
        final Member member = memberUtil.getCurrentMember();
        final MissionRecord missionRecord = findMissionRecordById(request.recordId());

        final Comment comment = createAndSaveComment(request, member, missionRecord);

        sendCommentNotification(missionRecord, comment, request.parentId());
        return CommentCreateResponse.of(comment.getId());
    }

    @Transactional(readOnly = true)
    public CommentFindResponse findCommentsByRecordId(Long recordId) {
        final MissionRecord missionRecord = findMissionRecordById(recordId);
        final List<Comment> allComments =
                commentRepository.findAllCommentsByMissionRecord(missionRecord);

        Map<Long, List<Comment>> commentsByParentId = groupCommentsByParentId(allComments);
        List<CommentFindOneResponse> rootResponses =
                convertToCommentFindOneResponses(commentsByParentId);

        return CommentFindResponse.of(rootResponses);
    }

    private Comment createAndSaveComment(
            CommentCreateRequest request, Member member, MissionRecord missionRecord) {
        final Comment comment =
                request.parentId() != null
                        ? Comment.createComment(
                                missionRecord,
                                member,
                                request.content(),
                                findCommentById(request.parentId()))
                        : Comment.createComment(missionRecord, member, request.content(), null);

        return commentRepository.save(comment);
    }

    /**
     * 댓글 알림 메서드
     *
     * @param missionRecord: 작성된 미션 기록
     * @param comment: 새로 생성한 댓글
     * @param parentId: 부모 댓글의 ID
     */
    private void sendCommentNotification(
            MissionRecord missionRecord, Comment comment, Long parentId) {
        Member missionRecordOwner = missionRecord.getMember();
        Member commentWriter = comment.getWriter();

        // 1. 부모 댓글이 없는 경우 게시물 작성자에게만 COMMENT 알림
        if (!missionRecordOwner.equals(commentWriter) && parentId == null) {
            sendCommentNotification(
                    missionRecordOwner,
                    FcmNotificationConstants.COMMENT,
                    missionRecord,
                    comment,
                    commentWriter);
            return;
        }

        // 2. 대댓글 작성 시 부모 댓글 작성자에게 RE_COMMENT 알림
        if (comment.getParent() != null) {
            // 부모 댓글 작성자
            Member parentCommentWriter = comment.getParent().getWriter();

            // 부모 댓글 작성자가 대댓글 작성자가 아닌 경우에만 알림 전송
            if (!parentCommentWriter.equals(commentWriter)) {
                sendCommentNotification(
                        parentCommentWriter,
                        FcmNotificationConstants.RE_COMMENT,
                        missionRecord,
                        comment,
                        commentWriter);
            }
            if (!missionRecordOwner.equals(commentWriter)) {
                // 게시물 작성자에게는 RECORD_RE_COMMENT 알림
                sendCommentNotification(
                        missionRecordOwner,
                        FcmNotificationConstants.RECORD_RE_COMMENT,
                        missionRecord,
                        comment,
                        commentWriter);
            }

            Set<Member> commentRecipients = collectNotificationRecipients(comment);
            commentRecipients.remove(parentCommentWriter);

            // Send notifications to unique recipients
            for (Member recipient : commentRecipients) {
                sendCommentNotification(
                        recipient,
                        FcmNotificationConstants.RE_COMMENT,
                        missionRecord,
                        comment,
                        commentWriter);
            }
        }
    }

    private void sendCommentNotification(
            Member recipient,
            FcmNotificationConstants notificationType,
            MissionRecord missionRecord,
            Comment comment,
            Member commentWriter) {
        String title = notificationType.getTitle();
        String message = commentWriter.getProfile().getNickname() + notificationType.getMessage();
        List<String> tokens = retrieveFcmTokens(Set.of(recipient));
        String notificationTypeName = notificationType.name();
        if (notificationType.name().equals(FcmNotificationConstants.RECORD_RE_COMMENT.name())) {
            notificationTypeName = FcmNotificationConstants.RE_COMMENT.name();
        }
        fcmNotificationService.sendAndNotifications(
                title,
                message,
                tokens,
                comment.getId(),
                missionRecord.getId(),
                FcmNotificationType.valueOf(notificationTypeName));
    }

    private Set<Member> collectNotificationRecipients(Comment comment) {
        Map<Long, Member> notificationRecipientsMap = new HashMap<>();

        Comment currentComment = comment;
        while (currentComment.getParent() != null) {
            currentComment = currentComment.getParent();
            currentComment.getReplyComments().stream()
                    .map(Comment::getWriter)
                    .forEach(writer -> notificationRecipientsMap.put(writer.getId(), writer));
        }
        notificationRecipientsMap.remove(comment.getWriter().getId());

        return new HashSet<>(notificationRecipientsMap.values());
    }

    private List<String> retrieveFcmTokens(Set<Member> notificationRecipients) {
        return notificationRecipients.stream()
                .flatMap(member -> fcmTokenRepository.findByMember(member).stream())
                .map(FcmToken::getToken)
                .filter(Objects::nonNull)
                .filter(token -> !token.isEmpty() && !token.isBlank())
                .collect(Collectors.toList());
    }

    private Map<Long, List<Comment>> groupCommentsByParentId(List<Comment> allComments) {
        return allComments.stream()
                .collect(
                        Collectors.groupingBy(
                                comment -> {
                                    Comment parent = comment.getParent();
                                    return (parent != null)
                                            ? parent.getId()
                                            : ROOT_COMMENT_PARENT_ID;
                                },
                                Collectors.toList()));
    }

    private List<CommentFindOneResponse> convertToCommentFindOneResponses(
            Map<Long, List<Comment>> commentsByParentId) {
        List<Comment> rootComments =
                commentsByParentId.getOrDefault(ROOT_COMMENT_PARENT_ID, List.of());
        return rootComments.stream()
                .map(comment -> convertToCommentFindOneResponse(comment, commentsByParentId))
                .collect(Collectors.toList());
    }

    private CommentFindOneResponse convertToCommentFindOneResponse(
            Comment comment, Map<Long, List<Comment>> commentsByParentId) {
        List<CommentFindOneResponse> replyCommentsResponses =
                commentsByParentId.getOrDefault(comment.getId(), List.of()).stream()
                        .map(
                                childComment ->
                                        convertToCommentFindOneResponse(
                                                childComment, commentsByParentId))
                        .collect(Collectors.toList());

        return CommentFindOneResponse.of(
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getId(),
                comment.getContent(),
                comment.getWriter().getId(),
                comment.getWriter().getProfile().getNickname(),
                comment.getWriter().getProfile().getProfileImageUrl(),
                comment.getCreatedAt().toString(),
                replyCommentsResponses);
    }

    private MissionRecord findMissionRecordById(Long recordId) {
        return missionRecordRepository
                .findById(recordId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_RECORD_NOT_FOUND));
    }

    private Comment findCommentById(Long commentId) {
        return commentRepository
                .findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
    }
}
