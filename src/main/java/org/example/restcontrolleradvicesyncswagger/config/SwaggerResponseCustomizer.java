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

@Component
public class SwaggerResponseCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Class<?> returnType = handlerMethod.getReturnType().getParameterType();
        
        // ApiResponse로 이미 래핑된 경우는 건너뜀
        if (returnType.getName().contains("ApiResponse")) {
            return operation;
        }
        
        // 모든 응답의 스키마를 ApiResponse 포맷으로 감싸기
        operation.getResponses().forEach((status, response) -> {
            if (response.getContent() != null) {
                response.getContent().forEach((mediaType, content) -> {
                    if (content.getSchema() != null) {
                        content.setSchema(wrapWithApiResponse(content.getSchema(), returnType, handlerMethod));
                    }
                });
            }
        });
        
        return operation;
    }
    
    private Schema<?> wrapWithApiResponse(Schema<?> originalSchema, Class<?> returnType, HandlerMethod handlerMethod) {
        ObjectSchema wrapper = new ObjectSchema();
        
        // data 필드 설정
        wrapper.addProperty("data", getDataSchema(originalSchema, returnType, handlerMethod));
        
        // page 필드 설정
        wrapper.addProperty("page", Page.class.isAssignableFrom(returnType) 
            ? createPageSchema() 
            : new Schema<>().type("object").nullable(true));
        
        return wrapper;
    }
    
    private Schema<?> getDataSchema(Schema<?> originalSchema, Class<?> returnType, HandlerMethod handlerMethod) {
        // Page 타입인 경우: Page<T>의 제네릭 타입을 추출해서 배열로 반환
        if (Page.class.isAssignableFrom(returnType)) {
            Type genericType = handlerMethod.getReturnType().getGenericParameterType();
            if (genericType instanceof ParameterizedType) {
                Type itemType = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                String itemClassName = ((Class<?>) itemType).getSimpleName();
                return new ArraySchema().items(new Schema<>().$ref("#/components/schemas/" + itemClassName));
            }
        }
        
        // 일반 타입(단일 객체 또는 List)인 경우: 원본 스키마 그대로 사용
        return originalSchema;
    }
    
    private Schema<?> createPageSchema() {
        ObjectSchema pageSchema = new ObjectSchema();
        pageSchema.addProperty("currentPage", new Schema<>().type("integer").example(1));
        pageSchema.addProperty("totalElement", new Schema<>().type("integer").example(3));
        pageSchema.addProperty("size", new Schema<>().type("integer").example(5));
        pageSchema.addProperty("totalPages", new Schema<>().type("integer").example(1));
        return pageSchema;
    }
}
