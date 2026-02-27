package nhnad.soeun_chat.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.InternalServerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AthenaService {

    @Value("${aws.athena.database}")
    private String database;

    @Value("${aws.athena.output-location}")
    private String outputLocation;

    private final AthenaClient athenaClient;

    public String executeQuery(String sql) {
        try {
            String queryExecutionId = startQuery(sql);
            waitForCompletion(queryExecutionId);
            return fetchResults(queryExecutionId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerException(ErrorCode.ATHENA_QUERY_FAILED);
        }
    }

    private String startQuery(String sql) {
        return athenaClient.startQueryExecution(StartQueryExecutionRequest.builder()
                .queryString(sql)
                .queryExecutionContext(QueryExecutionContext.builder()
                        .database(database)
                        .build())
                .resultConfiguration(ResultConfiguration.builder()
                        .outputLocation(outputLocation)
                        .build())
                .build()).queryExecutionId();
    }

    private void waitForCompletion(String queryExecutionId) throws InterruptedException {
        while (true) {
            QueryExecutionStatus status = athenaClient.getQueryExecution(
                    GetQueryExecutionRequest.builder()
                            .queryExecutionId(queryExecutionId)
                            .build()
            ).queryExecution().status();

            QueryExecutionState state = status.state();
            log.debug("Athena 쿼리 상태: {}", state);

            if (state == QueryExecutionState.SUCCEEDED) return;
            if (state == QueryExecutionState.FAILED || state == QueryExecutionState.CANCELLED) {
                log.error("Athena 쿼리 실패 - 원인: {}", status.stateChangeReason());
                throw new InternalServerException(ErrorCode.ATHENA_QUERY_FAILED);
            }
            Thread.sleep(500);
        }
    }

    private String fetchResults(String queryExecutionId) {
        GetQueryResultsResponse response = athenaClient.getQueryResults(
                GetQueryResultsRequest.builder()
                        .queryExecutionId(queryExecutionId)
                        .build());

        List<Row> rows = response.resultSet().rows();
        if (rows.size() <= 1) return "조회 결과 없음";

        List<String> headers = rows.get(0).data().stream()
                .map(Datum::varCharValue)
                .toList();

        int dataRowCount = rows.size() - 1;
        log.info("Athena 쿼리 결과: {}건", dataRowCount);

        return rows.subList(1, rows.size()).stream()
                .map(row -> {
                    List<String> values = row.data().stream()
                            .map(Datum::varCharValue)
                            .toList();
                    StringBuilder sb = new StringBuilder("{");
                    for (int i = 0; i < headers.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(headers.get(i)).append(": ").append(values.get(i));
                    }
                    return sb.append("}").toString();
                })
                .collect(Collectors.joining("\n"));
    }
}
