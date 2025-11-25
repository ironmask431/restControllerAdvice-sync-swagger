package org.example.restcontrolleradvicesyncswagger.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Swagger 응답 스키마 커스터마이저
 * 
 * <p>RestControllerAdvice에 의해 실제 API 응답이 ApiResponse 포맷으로 래핑되지만,
 * Swagger 문서에는 이러한 변환이 자동으로 반영되지 않는 문제를 해결합니다.</p>
 * 
 * <p>이 클래스는 SpringDoc의 OperationCustomizer를 구현하여 Swagger 문서 생성 시
 * 각 API의 응답 스키마를 런타임에 ApiResponse 포맷({data: ..., page: ...})으로 변환합니다.</p>
 * 
 * @author Your Name
 * @since 1.0
 */
@Component
public class SwaggerResponseCustomizer implements OperationCustomizer {

    /**
     * Swagger Operation을 커스터마이징하는 메인 메서드
     * 
     * <p>이 메서드는 SpringDoc이 각 API 엔드포인트의 문서를 생성할 때 자동으로 호출됩니다.
     * Controller 메서드의 반환 타입을 확인하고, 응답 스키마를 ApiResponse 포맷으로 감싸는 작업을 수행합니다.</p>
     * 
     * <h3>처리 흐름:</h3>
     * <ol>
     *   <li>Controller 메서드의 반환 타입 추출 (CompanyDTO, List&lt;CompanyDTO&gt;, Page&lt;CompanyDTO&gt; 등)</li>
     *   <li>이미 ApiResponse 타입인 경우 중복 래핑 방지를 위해 건너뜀</li>
     *   <li>모든 HTTP 응답 상태 코드(200, 400, 500 등)를 순회</li>
     *   <li>각 응답의 미디어 타입(application/json 등)별로 스키마 커스터마이징</li>
     *   <li>기존 스키마를 ApiResponse 포맷으로 래핑한 새로운 스키마로 교체</li>
     * </ol>
     * 
     * @param operation Swagger의 API Operation 객체 (각 엔드포인트를 나타냄)
     * @param handlerMethod Spring MVC의 Controller 메서드 정보 (반환 타입, 제네릭 타입 등)
     * @return 커스터마이징된 Operation 객체
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // Controller 메서드의 반환 타입 추출
        // 예: CompanyDTO.class, List.class, Page.class 등
        Class<?> returnType = handlerMethod.getReturnType().getParameterType();
        
        // ApiResponse로 이미 래핑된 경우는 건너뜀 (중복 래핑 방지)
        // Controller에서 직접 ApiResponse<T>를 반환하는 경우 이미 올바른 포맷이므로 변환 불필요
        if (returnType.getName().contains("ApiResponse")) {
            return operation;
        }
        
        // 모든 응답의 스키마를 ApiResponse 포맷으로 감싸기
        // operation.getResponses(): 200 OK, 400 Bad Request, 500 Internal Server Error 등 모든 HTTP 상태 코드별 응답
        operation.getResponses().forEach((status, response) -> {
            // response.getContent(): 응답의 Content 정보 (application/json, application/xml 등)
            if (response.getContent() != null) {
                // 각 미디어 타입별로 스키마 처리
                response.getContent().forEach((mediaType, content) -> {
                    if (content.getSchema() != null) {
                        // 기존 스키마를 ApiResponse 포맷으로 래핑한 새로운 스키마로 교체
                        // 예: CompanyDTO 스키마 → {data: CompanyDTO, page: null} 스키마
                        content.setSchema(wrapWithApiResponse(content.getSchema(), returnType, handlerMethod));
                    }
                });
            }
        });
        
        return operation;
    }
    
    /**
     * 원본 스키마를 ApiResponse 포맷으로 래핑
     * 
     * <p>이 메서드는 원본 스키마를 받아서 {data: ..., page: ...} 구조로 감싸는
     * 새로운 ObjectSchema를 생성합니다.</p>
     * 
     * <h3>생성되는 스키마 구조:</h3>
     * <pre>
     * {
     *   "data": &lt;실제 데이터 스키마&gt;,
     *   "page": &lt;페이징 정보 스키마 또는 null&gt;
     * }
     * </pre>
     * 
     * @param originalSchema Swagger가 자동 생성한 원본 스키마
     * @param returnType Controller 메서드의 반환 타입 (Page, List, 단일 객체 등)
     * @param handlerMethod Controller 메서드 정보 (제네릭 타입 추출에 사용)
     * @return ApiResponse 포맷으로 래핑된 새로운 스키마
     */
    private Schema<?> wrapWithApiResponse(Schema<?> originalSchema, Class<?> returnType, HandlerMethod handlerMethod) {
        // ApiResponse를 나타내는 ObjectSchema 생성
        ObjectSchema wrapper = new ObjectSchema();
        
        // "data" 필드 설정
        // getDataSchema()를 통해 반환 타입에 맞는 적절한 스키마 생성
        // - Page<T>인 경우: T의 배열 스키마 (content만 추출)
        // - 일반 타입인 경우: 원본 스키마 그대로
        wrapper.addProperty("data", getDataSchema(originalSchema, returnType, handlerMethod));
        
        // "page" 필드 설정 (조건부)
        // - Page 타입인 경우: 페이징 정보 스키마 (currentPage, totalElement, size, totalPages)
        // - 일반 타입인 경우: nullable object (null 값 허용)
        wrapper.addProperty("page", Page.class.isAssignableFrom(returnType) 
            ? createPageSchema() 
            : new Schema<>().type("object").nullable(true));
        
        return wrapper;
    }
    
    /**
     * data 필드의 스키마를 생성
     * 
     * <p>이 메서드는 Controller의 반환 타입에 따라 적절한 data 스키마를 생성합니다.
     * 특히 Page&lt;T&gt; 타입의 경우, Swagger가 자동 생성한 Page 객체 전체가 아닌
     * content 배열만 추출하여 data 필드에 포함시키는 특별한 처리를 수행합니다.</p>
     * 
     * <h3>처리 방식:</h3>
     * <ul>
     *   <li><b>Page&lt;CompanyDTO&gt;</b>: CompanyDTO[] 배열 스키마 생성 (Reflection 사용)
     *       <ul>
     *         <li>제네릭 타입(CompanyDTO)을 런타임에 추출</li>
     *         <li>Swagger의 components/schemas/CompanyDTO를 참조하는 배열 스키마 생성</li>
     *       </ul>
     *   </li>
     *   <li><b>List&lt;CompanyDTO&gt;</b>: 원본 스키마 그대로 사용 (이미 배열 형태)</li>
     *   <li><b>CompanyDTO</b>: 원본 스키마 그대로 사용 (단일 객체)</li>
     * </ul>
     * 
     * @param originalSchema Swagger가 자동 생성한 원본 스키마
     * @param returnType Controller 메서드의 반환 타입
     * @param handlerMethod Controller 메서드 정보 (제네릭 타입 추출에 사용)
     * @return data 필드에 사용될 스키마
     */
    private Schema<?> getDataSchema(Schema<?> originalSchema, Class<?> returnType, HandlerMethod handlerMethod) {
        // Page 타입인 경우: Page<T>의 제네릭 타입을 추출해서 배열로 반환
        if (Page.class.isAssignableFrom(returnType)) {
            // 1. 제네릭 타입 정보 가져오기
            // 예: Page<CompanyDTO> → java.lang.reflect.Type 객체
            Type genericType = handlerMethod.getReturnType().getGenericParameterType();
            
            // 2. ParameterizedType인지 확인 (제네릭 타입이 명시된 경우)
            // Page<CompanyDTO>는 ParameterizedType, 단순 Page는 아님
            if (genericType instanceof ParameterizedType) {
                // 3. 실제 타입 파라미터 추출
                // getActualTypeArguments()[0]: Page<CompanyDTO>에서 CompanyDTO를 추출
                Type itemType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                
                // 4. 클래스 이름 추출
                // CompanyDTO.class → "CompanyDTO" 문자열
                String itemClassName = ((Class<?>) itemType).getSimpleName();
                
                // 5. 배열 스키마 생성 및 반환
                // ArraySchema: JSON 배열을 나타냄
                // items(): 배열의 각 요소 타입 지정
                // $ref: Swagger의 components/schemas에 있는 스키마를 참조
                //       (Swagger가 DTO를 자동으로 components/schemas에 등록함)
                // 최종 결과: { "type": "array", "items": { "$ref": "#/components/schemas/CompanyDTO" } }
                return new ArraySchema().items(new Schema<>().$ref("#/components/schemas/" + itemClassName));
            }
        }
        
        // 일반 타입(단일 객체 또는 List)인 경우: 원본 스키마 그대로 사용
        // - CompanyDTO: Swagger가 자동 생성한 CompanyDTO 스키마
        // - List<CompanyDTO>: Swagger가 자동 생성한 CompanyDTO 배열 스키마
        return originalSchema;
    }
    
    /**
     * 페이징 정보 스키마를 생성
     * 
     * <p>이 메서드는 page 필드에 사용될 페이징 정보 스키마를 생성합니다.
     * ApiResponseAdvice에서 실제로 생성하는 PageInfo 객체의 구조와 동일합니다.</p>
     * 
     * <h3>생성되는 스키마:</h3>
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "currentPage": { "type": "integer", "example": 1 },
     *     "totalElement": { "type": "integer", "example": 3 },
     *     "size": { "type": "integer", "example": 5 },
     *     "totalPages": { "type": "integer", "example": 1 }
     *   }
     * }
     * </pre>
     * 
     * @return 페이징 정보 스키마
     */
    private Schema<?> createPageSchema() {
        ObjectSchema pageSchema = new ObjectSchema();
        
        // 각 필드 추가 (필드명, 타입, 예제 값)
        // addProperty(): ObjectSchema에 속성 추가
        // type(): 필드의 데이터 타입 지정 (integer, string, boolean 등)
        // example(): Swagger UI에 표시될 예제 값 지정
        
        pageSchema.addProperty("currentPage", new Schema<>().type("integer").example(1));
        pageSchema.addProperty("totalElement", new Schema<>().type("integer").example(3));
        pageSchema.addProperty("size", new Schema<>().type("integer").example(5));
        pageSchema.addProperty("totalPages", new Schema<>().type("integer").example(1));
        
        return pageSchema;
    }
}
