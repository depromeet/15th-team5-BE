package com.depromeet.stonebed.domain.auth.application;

import com.depromeet.stonebed.domain.auth.application.apple.AppleClient;
import com.depromeet.stonebed.domain.auth.application.kakao.KakaoClient;
import com.depromeet.stonebed.domain.auth.domain.OAuthProvider;
import com.depromeet.stonebed.domain.auth.dto.RefreshTokenDto;
import com.depromeet.stonebed.domain.auth.dto.request.RefreshTokenRequest;
import com.depromeet.stonebed.domain.auth.dto.response.AuthTokenResponse;
import com.depromeet.stonebed.domain.auth.dto.response.SocialClientResponse;
import com.depromeet.stonebed.domain.auth.dto.response.TokenPairResponse;
import com.depromeet.stonebed.domain.member.dao.MemberRepository;
import com.depromeet.stonebed.domain.member.domain.Member;
import com.depromeet.stonebed.domain.member.domain.MemberRole;
import com.depromeet.stonebed.domain.member.domain.MemberStatus;
import com.depromeet.stonebed.domain.member.domain.Profile;
import com.depromeet.stonebed.domain.member.dto.request.CreateMemberRequest;
import com.depromeet.stonebed.domain.member.dto.request.NicknameCheckRequest;
import com.depromeet.stonebed.global.error.ErrorCode;
import com.depromeet.stonebed.global.error.exception.CustomException;
import com.depromeet.stonebed.global.util.MemberUtil;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppleClient appleClient;
    private final KakaoClient kakaoClient;
    private final JwtTokenService jwtTokenService;
    private final MemberUtil memberUtil;
    private final MemberRepository memberRepository;

    public SocialClientResponse authenticateFromProvider(OAuthProvider provider, String token) {
        /* token
        1. apple의 경우 authorizationCode Value
        2. kakao의 경우 accessToken Value
         */
        return switch (provider) {
            case APPLE -> appleClient.authenticateFromApple(token);
            case KAKAO -> kakaoClient.authenticateFromKakao(token);
        };
    }

    public AuthTokenResponse socialLogin(
            OAuthProvider oAuthProvider, String oauthId, String email) {
        Optional<Member> memberOptional =
                memberRepository.findByOauthInfoOauthProviderAndOauthInfoOauthId(
                        oAuthProvider.getValue(), oauthId);

        return memberOptional
                .map(
                        member -> {
                            // 사용자 로그인 토큰 생성
                            TokenPairResponse tokenPair =
                                    member.getRole() == MemberRole.TEMPORARY
                                            ? getTemporaryLoginResponse(member)
                                            : getLoginResponse(member);
                            member.updateLastLoginAt();
                            updateMemberStatus(member);
                            return AuthTokenResponse.of(
                                    tokenPair, member.getRole() == MemberRole.TEMPORARY);
                        })
                .orElseGet(
                        () -> {
                            // 회원가입이 안된 경우, 임시 회원가입 진행
                            Member newMember =
                                    Member.createOAuthMember(oAuthProvider, oauthId, email);
                            memberRepository.save(newMember);

                            // 임시 토큰 발행
                            TokenPairResponse temporaryTokenPair =
                                    jwtTokenService.generateTemporaryTokenPair(newMember);
                            newMember.updateLastLoginAt();
                            return AuthTokenResponse.of(temporaryTokenPair, true);
                        });
    }

    // 회원가입
    public AuthTokenResponse registerMember(CreateMemberRequest request) {
        Member currentMember = memberUtil.getCurrentMember();
        // 사용자 회원가입
        if (memberUtil.getMemberRole().equals(MemberRole.TEMPORARY.getValue())) {
            // 닉네임 검증
            memberUtil.checkNickname(NicknameCheckRequest.of(request.nickname()));

            // 명시적 변경 감지
            Member registerMember = registerMember(currentMember, request);

            // 새 토큰 생성
            TokenPairResponse tokenPair = getLoginResponse(registerMember);
            return AuthTokenResponse.of(tokenPair, false);
        }
        throw new CustomException(ErrorCode.ALREADY_EXISTS_MEMBER);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse reissueTokenPair(RefreshTokenRequest request) {
        // 리프레시 토큰을 이용해 새로운 액세스 토큰 발급
        RefreshTokenDto refreshTokenDto =
                jwtTokenService.retrieveRefreshToken(request.refreshToken());
        RefreshTokenDto refreshToken =
                jwtTokenService.createRefreshTokenDto(refreshTokenDto.memberId());

        Member member = memberUtil.getMemberByMemberId(refreshToken.memberId());

        TokenPairResponse tokenPair = getLoginResponse(member);
        return AuthTokenResponse.of(tokenPair, false);
    }

    private TokenPairResponse getLoginResponse(Member member) {
        return jwtTokenService.generateTokenPair(member.getId(), MemberRole.USER);
    }

    private TokenPairResponse getTemporaryLoginResponse(Member member) {
        return jwtTokenService.generateTokenPair(member.getId(), MemberRole.TEMPORARY);
    }

    public void withdraw() {
        Member member = memberUtil.getCurrentMember();
        /**
         * TODO: 런칭데이 이후 고도화 if (provider.equals(OAuthProvider.APPLE)) {
         * appleClient.withdraw(member.getOauthInfo().getOauthId()); }
         */
        validateMemberStatusDelete(member.getStatus());
        member.updateMemberRole(MemberRole.TEMPORARY);
        memberRepository.flush();

        jwtTokenService.deleteRefreshToken(member.getId());
        memberRepository.deleteById(member.getId());
    }

    private void validateMemberStatusDelete(MemberStatus status) {
        if (status == MemberStatus.DELETED) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_DELETED);
        }
    }

    private Member registerMember(Member member, CreateMemberRequest request) {
        member.updateProfile(
                Profile.createProfile(
                        request.nickname(), member.getProfile().getProfileImageUrl()));
        member.updateRaisePet(request.raisePet());
        member.updateMemberRole(MemberRole.USER);
        memberRepository.save(member);
        return member;
    }

    private void updateMemberStatus(Member member) {
        if (member.getStatus() == MemberStatus.DELETED) {
            member.updateStatus(MemberStatus.NORMAL);
        }
    }
}
