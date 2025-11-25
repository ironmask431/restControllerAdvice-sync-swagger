# RestControllerAdvice와 Swagger 동기화 프로젝트

## 📌 프로젝트 개요

Spring Boot REST API에서 `@RestControllerAdvice`를 사용하여 모든 API 응답을 공통 포맷으로 통일할 때, **Swagger 문서의 Response 영역에는 이러한 변환이 반영되지 않는 문제**를 해결하는 프로젝트입니다.

### 문제 상황

```java
// Controller에서는 Page<CompanyDTO> 를 반환
@GetMapping("/page")
public Page<CompanyDTO> getCompanyPage(
        @Parameter(description = "페이징 정보 (page, size, sort)")
        Pageable pageable) {

    // 예시 데이터
    List<CompanyDTO> content = List.of(
            new CompanyDTO(1L, "회사1", "주소1"),
            new CompanyDTO(2L, "회사2", "주소2"),
            new CompanyDTO(3L, "회사3", "주소3")
    );

    return new PageImpl<>(content, pageable, content.size());
}
```

```json
// 실제 API 응답은 RestControllerAdvice에 의해 래핑됨
{
  "data": [
    {
      "id": 1,
      "name": "삼성전자",
      "address": "서울시 강남구"
    }
  ],
  "page": {
    "currentPage": 1,
    "totalElement": 3,
    "size": 20,
    "totalPages": 1
  }
}
```

하지만 **Swagger UI에는 원본 응답 형태(`CompanyDTO`)만 표시**되어, 실제 API 응답과 문서가 불일치하는 문제가 발생합니다.

```json
// swagger response 영역에 표기되는 응답 형태
{
  "totalPages": 0,
  "totalElements": 0,
  "first": true,
  "last": true,
  "size": 0,
  "content": [
    {
      "id": 1,
      "name": "삼성전자",
      "address": "서울시 강남구"
    }
  ],
  "number": 0,
  "sort": {
    "empty": true,
    "sorted": true,
    "unsorted": true
  },
  "pageable": {
    "offset": 0,
    "sort": {
      "empty": true,
      "sorted": true,
      "unsorted": true
    },
    "pageNumber": 0,
    "pageSize": 0,
    "paged": true,
    "unpaged": true
  },
  "numberOfElements": 0,
  "empty": true
}
```

---

## 🎯 프로젝트 목표

1. **단일 객체, List, Page 응답을 통일된 포맷으로 자동 변환**
2. **Controller 코드 수정 없이 공통 응답 포맷 적용**
3. **Swagger 문서에 실제 API 응답 형태 정확히 반영**

---

## 🏗️ 아키텍처

### 1. 공통 응답 포맷

모든 API 응답은 다음과 같은 구조를 따릅니다:

```json
{
  "data": <실제 데이터>,
  "page": <페이징 정보 또는 null>
}
```

#### 단건 조회 응답
```json
{
  "data": {
    "id": 1,
    "name": "회사1",
    "address": "주소1"
  },
  "page": null
}
```

#### 리스트 조회 응답
```json
{
  "data": [
    {"id": 1, "name": "회사1", "address": "주소1"},
    {"id": 2, "name": "회사2", "address": "주소2"}
  ],
  "page": null
}
```

#### 페이징 조회 응답
```json
{
  "data": [
    {"id": 1, "name": "회사1", "address": "주소1"},
    {"id": 2, "name": "회사2", "address": "주소2"}
  ],
  "page": {
    "currentPage": 1,
    "totalElement": 10,
    "size": 5,
    "totalPages": 2
  }
}
```

---

## 💡 핵심 구현

### 1. 공통 응답 DTO

**ApiResponse.java**
```java
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T data;
    private PageInfo page;
    
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }
    
    public static <T> ApiResponse<T> of(T data, PageInfo pageInfo) {
        return new ApiResponse<>(data, pageInfo);
    }
}
```

**PageInfo.java**
```java
@Getter
@AllArgsConstructor
public class PageInfo {
    private int currentPage;
    private long totalElement;
    private int size;
    private int totalPages;
    
    public static PageInfo from(Page<?> page) {
        return new PageInfo(
            page.getNumber() + 1,
            page.getTotalElements(),
            page.getSize(),
            page.getTotalPages()
        );
    }
}
```

### 2. RestControllerAdvice

**ApiResponseAdvice.java**
```java
@RestControllerAdvice(basePackages = "org.example.restcontrolleradvicesyncswagger.controller")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, 
                          Class<? extends HttpMessageConverter<?>> converterType) {
        // ApiResponse로 이미 감싸진 응답은 제외
        return !returnType.getParameterType().equals(ApiResponse.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, ...) {
        // Swagger UI 관련 요청은 변환하지 않음
        String path = request.getURI().getPath();
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            return body;
        }
        
        // Page 객체인 경우
        if (body instanceof Page<?> page) {
            return ApiResponse.of(page.getContent(), PageInfo.from(page));
        }
        
        // 일반 객체인 경우
        return ApiResponse.of(body);
    }
}
```

### 3. Swagger 문서 커스터마이징 (핵심!)

**SwaggerResponseCustomizer.java**
```java
@Component
public class SwaggerResponseCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Class<?> returnType = handlerMethod.getReturnType().getParameterType();
        
        if (returnType.getName().contains("ApiResponse")) {
            return operation;
        }
        
        // 모든 응답의 스키마를 ApiResponse 포맷으로 감싸기
        operation.getResponses().forEach((status, response) -> {
            if (response.getContent() != null) {
                response.getContent().forEach((mediaType, content) -> {
                    if (content.getSchema() != null) {
                        content.setSchema(wrapWithApiResponse(...));
                    }
                });
            }
        });
        
        return operation;
    }
    
    private Schema<?> wrapWithApiResponse(...) {
        ObjectSchema wrapper = new ObjectSchema();
        wrapper.addProperty("data", getDataSchema(...));
        wrapper.addProperty("page", ...);
        return wrapper;
    }
}
```

---

## 🚀 사용 방법

### 1. Controller 작성

Controller는 원래대로 작성하면 됩니다. **추가 코드 불필요!**

```java
@RestController
@RequestMapping("/company")
public class CompanyController {

    @GetMapping("/{id}")
    public CompanyDTO getCompany(@PathVariable Long id) {
        return new CompanyDTO(id, "회사" + id, "주소" + id);
    }

    @GetMapping("/list")
    public List<CompanyDTO> getCompanyList() {
        return List.of(
            new CompanyDTO(1L, "회사1", "주소1"),
            new CompanyDTO(2L, "회사2", "주소2")
        );
    }

    @GetMapping("/page")
    public Page<CompanyDTO> getCompanyPage(Pageable pageable) {
        List<CompanyDTO> content = List.of(...);
        return new PageImpl<>(content, pageable, content.size());
    }
}
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. Swagger UI 확인

브라우저에서 `http://localhost:8080/swagger-ui/index.html` 접속

<img width="425" height="374" alt="스크린샷 2025-11-25 오후 10 49 19" src="https://github.com/user-attachments/assets/b9876268-d83d-446f-a7f1-f951f723d228" /><br>

<img width="447" height="488" alt="스크린샷 2025-11-25 오후 10 49 30" src="https://github.com/user-attachments/assets/32ccf1c2-145d-450b-9865-eab1262d6618" /><br>

<img width="449" height="474" alt="스크린샷 2025-11-25 오후 10 49 38" src="https://github.com/user-attachments/assets/b85d897f-9473-4f74-9dcd-6ecb65e0cfc4" />   

---

## 🔧 기술 스택

- **Spring Boot** 3.2.5
- **Java** 21
- **SpringDoc OpenAPI** 2.2.0
- **Lombok**
- **Spring Data JPA**
- **MySQL**

---

## 📂 프로젝트 구조

```
src/main/java/org/example/restcontrolleradvicesyncswagger/
├── advice/
│   └── ApiResponseAdvice.java          # ResponseBodyAdvice 구현
├── config/
│   ├── SwaggerConfig.java              # Swagger 기본 설정
│   └── SwaggerResponseCustomizer.java  # Swagger 응답 커스터마이징 (핵심!)
├── controller/
│   └── CompanyController.java          # REST API 컨트롤러
├── dto/
│   ├── ApiResponse.java                # 공통 응답 래퍼
│   ├── PageInfo.java                   # 페이징 정보
│   └── CompanyDTO.java                 # 도메인 DTO
└── RestControllerAdviceSyncSwaggerApplication.java
```

---

## ✨ 핵심 포인트

### 1. Controller 코드 무변경
- Controller는 기존처럼 `CompanyDTO`, `List<CompanyDTO>`, `Page<CompanyDTO>`를 반환
- `@RestControllerAdvice`가 자동으로 `ApiResponse`로 래핑

### 2. Swagger 문서 자동 동기화
- `OperationCustomizer`를 통해 Swagger 스키마를 런타임에 커스터마이징
- 실제 API 응답과 Swagger 문서가 완벽히 일치

### 3. Page 타입 특별 처리
- `Page<T>` 반환 시 `content`만 추출하여 `data`에 포함
- 페이징 메타데이터는 별도의 `page` 필드로 제공

---

## 🎓 학습 포인트

1. **ResponseBodyAdvice**: Spring MVC의 응답 변환 메커니즘
2. **OperationCustomizer**: SpringDoc의 API 문서 커스터마이징 인터페이스
3. **제네릭 타입 추출**: Reflection을 이용한 `Page<T>`의 타입 파라미터 추출
4. **Swagger Schema 조작**: OpenAPI 스키마를 프로그래밍 방식으로 생성

---

## 📝 참고사항

### Swagger UI에서 변환 제외
```java
String path = request.getURI().getPath();
if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
    return body; // Swagger 관련 요청은 변환하지 않음
}
```

### ApiResponse 중복 래핑 방지
```java
@Override
public boolean supports(MethodParameter returnType, ...) {
    return !returnType.getParameterType().equals(ApiResponse.class);
}
```
