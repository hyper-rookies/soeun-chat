package nhnad.soeun_chat.domain.share.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.repository.ConversationRepository;
import nhnad.soeun_chat.domain.chat.repository.MessageRepository;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.conversation.dto.MessageItem;
import nhnad.soeun_chat.domain.share.dto.ShareCreateResponse;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.EntityNotFoundException;
import nhnad.soeun_chat.global.exception.ForbiddenException;
import nhnad.soeun_chat.global.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    @Value("${share.jwt-secret}")
    private String jwtSecret;

    @Value("${share.expiration-days}")
    private int expirationDays;

    @Value("${share.base-url}")
    private String baseUrl;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ShareCreateResponse generateShareToken(String conversationId, String userId) {
        Map<String, AttributeValue> item = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        String owner = attrOrDefault(item, "userId", "");
        if (!userId.equals(owner)) {
            log.warn("공유 링크 생성 권한 없음 - conversationId: {}, userId: {}", conversationId, userId);
            throw new ForbiddenException();
        }

        Instant expiry = Instant.now().plus(expirationDays, ChronoUnit.DAYS);

        String token = Jwts.builder()
                .subject(conversationId)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();

        log.info("공유 링크 생성 - conversationId: {}", conversationId);
        return new ShareCreateResponse(
                token,
                baseUrl + "/shared/" + token,
                expiry.atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        );
    }

    public ConversationResponse getSharedConversation(String token) {
        String conversationId = validateShareToken(token);

        Map<String, AttributeValue> item = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        long now = Instant.now().toEpochMilli();

        List<MessageItem> messages = messageRepository.findByConversationId(conversationId).stream()
                .map(msg -> new MessageItem(
                        attr(msg, "messageId"),
                        attr(msg, "role"),
                        attr(msg, "content"),
                        attr(msg, "createdAt")
                ))
                .toList();

        return new ConversationResponse(
                attr(item, "conversationId"),
                attrOrDefault(item, "title", "새 대화"),
                attrNOrDefault(item, "createdAt", now),
                attrNOrDefault(item, "updatedAt", now),
                messages
        );
    }

    private String validateShareToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (ExpiredJwtException e) {
            log.warn("공유 토큰 만료: {}", e.getMessage());
            throw new UnauthorizedException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException e) {
            log.warn("공유 토큰 검증 실패: {}", e.getMessage());
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN);
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String attr(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }

    private String attrOrDefault(Map<String, AttributeValue> item, String key, String defaultValue) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : defaultValue;
    }

    private Long attrNOrDefault(Map<String, AttributeValue> item, String key, Long defaultValue) {
        AttributeValue v = item.get(key);
        return (v != null && v.n() != null) ? Long.parseLong(v.n()) : defaultValue;
    }
}
