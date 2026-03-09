package nhnad.soeun_chat.domain.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String email,
        String name
) {}
