package com.depromeet.stonebed.domain.auth.dto.response;

public record AppleTokenResponse(
        // 외부 통신 시 snake_case로 요청 및 응답
        String access_token,
        String expires_in,
        String id_token,
        String refresh_token,
        String token_type,
        String error) {
    public static AppleTokenResponse of(
            String accessToken,
            String expiresIn,
            String idToken,
            String refreshToken,
            String tokenType,
            String error) {
        return new AppleTokenResponse(
                accessToken, expiresIn, idToken, refreshToken, tokenType, error);
    }
}