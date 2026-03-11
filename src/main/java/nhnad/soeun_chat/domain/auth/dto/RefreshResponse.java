package nhnad.soeun_chat.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 갱신 응답")
public record RefreshResponse(
        @Schema(description = "새로 발급된 액세스 토큰", example = "eyJraWQiOiJhYmMxMjMi...")
        String accessToken,

        @Schema(description = "액세스 토큰 만료까지 남은 시간 (초)", example = "3600")
        long expiresIn
) {}
