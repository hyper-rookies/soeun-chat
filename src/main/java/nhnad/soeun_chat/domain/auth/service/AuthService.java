package nhnad.soeun_chat.domain.auth.service;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.auth.dto.AuthResponse;
import nhnad.soeun_chat.domain.auth.dto.RefreshResponse;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.UnauthorizedException;
import nhnad.soeun_chat.global.jwt.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private final JwtValidator jwtValidator;
    private final RestTemplate restTemplate;
    private final String cognitoDomain;
    private final String clientId;
    private final String clientSecret;

    public AuthService(JwtValidator jwtValidator,
                       @Value("${aws.cognito.domain}") String cognitoDomain,
                       @Value("${aws.cognito.client-id}") String clientId,
                       @Value("${aws.cognito.client-secret}") String clientSecret) {
        this.jwtValidator = jwtValidator;
        this.cognitoDomain = cognitoDomain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restTemplate = new RestTemplate();
    }

    public AuthResponse callback(String code, String redirectUri) {
        Map<String, Object> tokenResponse = exchangeCode(code, redirectUri);

        String idToken = (String) tokenResponse.get("id_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");

        JWTClaimsSet claims;
        try {
            claims = jwtValidator.validate(idToken);
        } catch (Exception e) {
            log.warn("Cognito ID 토큰 검증 실패: {}", e.getMessage());
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        String userId = claims.getSubject();
        String email = (String) claims.getClaim("email");
        String name = (String) claims.getClaim("name");

        log.info("Cognito 로그인 성공 - userId: {}, email: {}", userId, email);

        return new AuthResponse(idToken, refreshToken, userId, email, name);
    }

    public RefreshResponse refresh(String refreshToken) {
        String tokenUrl = cognitoDomain + "/oauth2/token";

        String encoded = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + encoded);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        Map<String, Object> response;
        try {
            response = restTemplate.postForObject(tokenUrl, request, Map.class);
        } catch (RestClientException e) {
            log.warn("Cognito refresh token exchange 실패: {}", e.getMessage());
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        if (response == null || !response.containsKey("id_token")) {
            log.warn("Cognito refresh 응답에 id_token 없음: {}", response);
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        String newIdToken = (String) response.get("id_token");
        long expiresIn = response.containsKey("expires_in")
                ? ((Number) response.get("expires_in")).longValue()
                : 3600L;

        log.info("Cognito 토큰 갱신 성공");
        return new RefreshResponse(newIdToken, expiresIn);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCode(String code, String redirectUri) {
        String tokenUrl = cognitoDomain + "/oauth2/token";

        String encoded = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + encoded);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        Map<String, Object> response;
        try {
            response = restTemplate.postForObject(tokenUrl, request, Map.class);
        } catch (RestClientException e) {
            log.warn("Cognito token exchange 실패: {}", e.getMessage());
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        if (response == null || !response.containsKey("id_token")) {
            log.warn("Cognito 응답에 id_token 없음: {}", response);
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }

        return response;
    }
}
