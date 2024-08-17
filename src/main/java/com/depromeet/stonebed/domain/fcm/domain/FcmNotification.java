package com.depromeet.stonebed.domain.fcm.domain;

import com.depromeet.stonebed.domain.common.BaseTimeEntity;
import com.depromeet.stonebed.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmNotification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FcmNotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column private String notificationImageUrl;

    @Column private Long targetId;

    @Column(nullable = false)
    private Boolean isRead = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private FcmNotification(
            FcmNotificationType type,
            String title,
            String message,
            String notificationImageUrl,
            Member member,
            Long targetId,
            Boolean isRead) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.notificationImageUrl = notificationImageUrl;
        this.member = member;
        this.targetId = targetId;
        this.isRead = isRead;
    }

    public static FcmNotification create(
            FcmNotificationType type,
            String title,
            String message,
            String notificationImageUrl,
            Member member,
            Long targetId,
            Boolean isRead) {
        return new FcmNotification(
                type, title, message, notificationImageUrl, member, targetId, isRead);
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
