package nhnad.soeun_chat.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 성공 응답")
public record AuthResponse(
        @Schema(description = "Cognito JWT 액세스 토큰 — 모든 API 호출 시 Authorization: Bearer {accessToken} 헤더에 포함", example = "eyJraWQiOiJhYmMxMjMi...")
        String accessToken,

        @Schema(description = "리프레시 토큰 — accessToken 만료 시 갱신에 사용 (POST /api/auth/refresh)", example = "eyJjdHkiOiJKV1Qi...")
        String refreshToken,

        @Schema(description = "사용자 고유 ID (Cognito sub, UUID 형식)", example = "550e8400-e29b-41d4-a716-446655440000")
        String userId,

        @Schema(description = "사용자 이메일", example = "user@example.com")
        String email,

        @Schema(description = "사용자 이름", example = "홍길동")
        String name
) {}
