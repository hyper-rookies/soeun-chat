package nhnad.soeun_chat.domain.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공유 링크 생성 응답")
public record ShareCreateResponse(
        @Schema(description = "공유 토큰 — GET /api/share/{token} 에 사용", example = "sh_eyJhbGci...")
        String shareToken,

        @Schema(description = "완성된 공유 URL", example = "https://soeun-report.vercel.app/share/sh_eyJhbGci...")
        String shareUrl,

        @Schema(description = "공유 링크 만료 시각 (ISO 8601 형식)", example = "2024-04-07T10:00:00")
        LocalDateTime expiresAt
) {}
