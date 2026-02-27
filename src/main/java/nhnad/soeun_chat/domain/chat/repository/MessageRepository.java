package nhnad.soeun_chat.domain.chat.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MessageRepository {

    private static final String TABLE_NAME = "se_messages";
    // DynamoDB GSI: conversationId(PK) + createdAt(SK)
    private static final String GSI_NAME = "conversationId-createdAt-index";

    private final DynamoDbClient dynamoDbClient;

    public List<Map<String, AttributeValue>> findByConversationId(String conversationId) {
        QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName(GSI_NAME)
                .keyConditionExpression("conversationId = :cid")
                .expressionAttributeValues(Map.of(":cid", AttributeValue.fromS(conversationId)))
                .scanIndexForward(false)  // 최신순(내림차순) 조회
                .limit(10)
                .build());

        List<Map<String, AttributeValue>> items = new ArrayList<>(response.items());
        Collections.reverse(items);  // 오름차순으로 변환
        log.debug("메시지 히스토리 조회 - conversationId: {}, count: {}", conversationId, items.size());
        return items;
    }

    public void save(String conversationId, String role, String content) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("messageId", AttributeValue.fromS(UUID.randomUUID().toString()));
        item.put("conversationId", AttributeValue.fromS(conversationId));
        item.put("role", AttributeValue.fromS(role));
        item.put("content", AttributeValue.fromS(content));
        item.put("createdAt", AttributeValue.fromS(Instant.now().toString()));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
        log.debug("메시지 저장 - conversationId: {}, role: {}", conversationId, role);
    }
}
