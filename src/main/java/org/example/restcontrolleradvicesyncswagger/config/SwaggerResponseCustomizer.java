package org.example.restcontrolleradvicesyncswagger.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springdoc.core.customizers.OperationCustomizer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * **Swagger 응답 스키마 커스터마이저**
 * * <p>API 응답이 {@code ApiResponse} 포맷으로 래핑될 때, Swagger 문서도 이에 맞게 동적으로 변환합니다.</p>
 */
@Component
public class SwaggerResponseCustomizer implements OperationCustomizer {

    /**
     * Swagger Operation 커스터마이징.
     * * <p>Controller 반환 타입을 확인하여 응답 스키마를 {@code ApiResponse} 포맷으로 래핑합니다.</p>
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // Controller 메서드의 반환 타입 추출
        Class<?> returnType = handlerMethod.getReturnType().getParameterType();

        // 이미 ApiResponse로 래핑된 경우는 중복 방지를 위해 건너뜜
        if (returnType.getName().contains("ApiResponse")) {
            return operation;
        }

        // 모든 HTTP 응답 상태 코드(200, 400 등)를 순회하며 스키마를 ApiResponse로 래핑
        operation.getResponses().forEach((status, response) -> {
            if (response.getContent() != null) {
                // 각 미디어 타입별로 스키마 처리
                response.getContent().forEach((mediaType, content) -> {
                    if (content.getSchema() != null) {
                        // 기존 스키마를 ApiResponse 포맷({data: ..., page: ...})으로 래핑한 새 스키마로 교체
                        content.setSchema(wrapWithApiResponse(content.getSchema(), returnType, handlerMethod));
                    }
                });
            }
        });

        return operation;
    }

    /**
     * 원본 스키마를 {@code ApiResponse} 포맷({data: ..., page: ...})으로 래핑하는 스키마를 생성합니다.
     */
    private Schema<?> wrapWithApiResponse(Schema<?> originalSchema, Class<?> returnType, HandlerMethod handlerMethod) {
        ObjectSchema wrapper = new ObjectSchema();

        // "data" 필드 설정: 반환 타입에 따라 적절한 데이터 스키마 생성
        wrapper.addProperty("data", getDataSchema(originalSchema, returnType, handlerMethod));

        // "page" 필드 설정: Page 타입인 경우 페이징 스키마, 아니면 nullable object
        wrapper.addProperty("page", Page.class.isAssignableFrom(returnType)
                ? createPageSchema()
                : new Schema<>().type("object").nullable(true));

        return wrapper;
    }

    /**
     * **data 필드 스키마 생성 및 Page 타입 특별 처리**
     * * <p>{@code Page<T>} 타입인 경우, {@code T}의 배열 스키마를 추출하여 {@code data} 필드에 사용합니다.
     * 그 외 타입은 원본 스키마를 그대로 사용합니다.</p>
     */
    private Schema<?> getDataSchema(Schema<?> originalSchema, Class<?> returnType, HandlerMethod handlerMethod) {
        // Page 타입인 경우: Page<T>의 제네릭 T를 추출하여 T의 배열 스키마로 변환
        if (Page.class.isAssignableFrom(returnType)) {
            Type genericType = handlerMethod.getReturnType().getGenericParameterType();

            if (genericType instanceof ParameterizedType) {
                // Page<T>에서 T를 추출하여 배열 스키마 생성
                Type itemType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                String itemClassName = ((Class<?>) itemType).getSimpleName();

                return new ArraySchema().items(new Schema<>().$ref("#/components/schemas/" + itemClassName));
            }
        }

        // 일반 타입(단일 객체 또는 List)인 경우: 원본 스키마 그대로 사용
        return originalSchema;
    }

    /**
     * **페이징 정보({@code PageInfo}) 스키마 생성**
     * * <p>{@code page} 필드에 사용될 페이징 정보(currentPage, totalElement 등) 스키마를 정의합니다.</p>
     */
    private Schema<?> createPageSchema() {
        ObjectSchema pageSchema = new ObjectSchema();

        pageSchema.addProperty("currentPage", new Schema<>().type("integer").example(1));
        pageSchema.addProperty("totalElement", new Schema<>().type("integer").example(3));
        pageSchema.addProperty("size", new Schema<>().type("integer").example(5));
        pageSchema.addProperty("totalPages", new Schema<>().type("integer").example(1));

        return pageSchema;
    }
}