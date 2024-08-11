package com.depromeet.stonebed.domain.fcm.domain;

import com.depromeet.stonebed.domain.common.BaseTimeEntity;
import com.depromeet.stonebed.domain.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmToken extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(unique = true, nullable = false)
    private String token;

    public FcmToken(Member member, String token) {
        this.member = member;
        this.token = token;
    }

    public void updateToken(String token) {
        this.token = token;
    }
}
