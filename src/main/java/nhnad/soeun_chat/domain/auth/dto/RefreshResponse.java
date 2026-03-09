package nhnad.soeun_chat.domain.auth.dto;

public record RefreshResponse(
        String accessToken,
        long expiresIn
) {}
