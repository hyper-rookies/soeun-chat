package nhnad.soeun_chat.domain.account.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.UnauthorizedException;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalUserController {

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    private final CognitoIdentityProviderClient cognitoClient;

    public record UserItem(String userId, String email) {}

    @GetMapping("/users")
    public ApiResponse<List<UserItem>> listUsers(
            @RequestHeader("X-Internal-Key") String internalKey) {

        if (!internalApiKey.equals(internalKey)) {
            log.warn("내부 API 키 인증 실패");
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        var response = cognitoClient.listUsers(
                ListUsersRequest.builder()
                        .userPoolId(userPoolId)
                        .build());

        List<UserItem> users = response.users().stream()
                .map(this::toUserItem)
                .filter(u -> u.userId() != null)
                .toList();

        log.info("유저 목록 조회 완료 - count: {}", users.size());
        return ApiResponse.of(users);
    }

    private UserItem toUserItem(UserType user) {
        String userId = null;
        String email = null;
        for (AttributeType attr : user.attributes()) {
            if ("sub".equals(attr.name())) userId = attr.value();
            if ("email".equals(attr.name())) email = attr.value();
        }
        return new UserItem(userId, email);
    }
}
