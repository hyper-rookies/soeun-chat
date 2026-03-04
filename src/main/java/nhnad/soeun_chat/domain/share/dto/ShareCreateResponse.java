package nhnad.soeun_chat.domain.share.dto;

import java.time.LocalDateTime;

public record ShareCreateResponse(
        String shareToken,
        String shareUrl,
        LocalDateTime expiresAt
) {
}
