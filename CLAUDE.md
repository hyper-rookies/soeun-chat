# SE Report 프로젝트 컨텍스트

## 프로젝트 개요
카카오 모먼트 + 구글 검색광고 데이터를 수집하여 자연어 대화로 성과를 조회/분석하는 AI 리포트 시스템.
핵심 가치: "대시보드를 보지 말고, 대화하세요."

## 기술 스택
- **프론트엔드**: Next.js
- **백엔드**: Spring Boot 3.x (Java 17), EC2 (Ubuntu 24.04)
- **AI**: AWS Bedrock (Claude)
- **인증**: AWS Cognito (Google OAuth)
- **데이터**: S3 (파티셔닝 Parquet) + Athena + Glue Catalog
- **NoSQL**: DynamoDB (채팅기록, 리포트, 광고계정)
- **배치**: EventBridge → SQS → Lambda
- **CI/CD**: GitHub Actions → ECR → EC2

## AWS 리소스
| 리소스 | 이름 |
|---|---|
| EC2 | se-report-ec2 (t3.small, Ubuntu 24.04) |
| ECR | se-report-server |
| DynamoDB | se_ad_accounts, se_conversations, se_messages, se_reports |
| Cognito User Pool | User pool - nujkmr |
| API Gateway | se-report-api (prod 스테이지) |
| S3 버킷 | se-report-ad-data |
| Glue DB | se_report_db |
| Glue Table | se_ad_performance_parquet |
| 배치 Lambda | se-batch-lambda (Python 3.12) |

## S3 파티셔닝 구조
```
se-report-ad-data/
└── raw/
    ├── platform=google/year=YYYY/month=MM/day=DD/data.parquet
    └── platform=kakao/year=YYYY/month=MM/day=DD/data.parquet
```

## Glue/Athena 스키마 (se_ad_performance_parquet)
| 컬럼 | 타입 |
|---|---|
| date | string |
| campaign_id | string |
| campaign_name | string |
| ad_group_id | string |
| ad_group_name | string |
| impressions | bigint |
| clicks | bigint |
| cost | double |
| conversions | bigint |
| conversion_value | double |
| platform | string (파티션) |
| year | string (파티션) |
| month | string (파티션) |
| day | string (파티션) |

## Spring Boot 프로젝트 구조
```
src/main/java/nhnad/soeun_chat/
├── global/
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── CorsConfig.java
│   │   └── SwaggerConfig.java
│   ├── jwt/
│   │   ├── JwtValidator.java
│   │   ├── JwtAuthFilter.java
│   │   ├── JwtAuthenticationEntryPoint.java
│   │   └── ExceptionHandlerFilter.java
│   ├── exception/
│   ├── error/
│   └── response/
│       └── ApiResponse.java
└── domain/
    ├── auth/       (미구현)
    ├── chat/       (미구현)
    ├── report/     (미구현)
    └── account/    (미구현)
```

## 공통 응답 포맷
```json
// 성공
{ "success": true, "data": { ... } }
// 실패
{ "success": false, "code": "ErrorCode", "message": "에러 메시지" }
```

## 채팅/스트리밍 흐름
```
Client → API Gateway → EC2 (Spring Boot)
EC2:
  1. DynamoDB에서 이전 대화 컨텍스트 조회
  2. Bedrock으로 SQL 생성
  3. Athena로 S3 Parquet 쿼리 실행
  4. Bedrock으로 최종 답변 스트리밍 (SseEmitter)
  5. DynamoDB에 대화 기록 저장
```

## SSE 인증
- API Gateway 29초 타임아웃 우회를 위해 EC2에서 JWKS 직접 검증
- SSE 스트리밍: Client → EC2 직접 연결

## 주요 결정사항
| 항목 | 결정 | 이유 |
|---|---|---|
| 채팅 서버 | EC2 (Spring Boot) | SSE 스트리밍 연결 유지 필요 |
| DB | DynamoDB | 단순 조회, 팀 표준 |
| 광고 데이터 형식 | Parquet (SNAPPY 압축) | Athena 비용 절감 |
| SSE 인증 | EC2에서 JWKS 직접 검증 | API Gateway 타임아웃 우회 |
| 공유링크 JWT | DynamoDB se_reports 저장 | 만료 관리 필요 |

## 다음 구현할 것 (우선순위 순)
1. 샘플 데이터 S3 적재 & Athena 쿼리 테스트
2. chat 도메인 구현
    - ChatController (SSE 스트리밍)
    - ChatService (Bedrock SQL 생성 → Athena 쿼리 → Bedrock 답변 스트리밍)
    - DynamoDB 대화 기록 저장/조회
3. report 도메인 구현
4. account 도메인 구현