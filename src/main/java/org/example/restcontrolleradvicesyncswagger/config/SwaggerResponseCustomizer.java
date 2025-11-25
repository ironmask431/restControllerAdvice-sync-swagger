package org.example.restcontrolleradvicesyncswagger.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

@Component
public class SwaggerResponseCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Class<?> returnType = handlerMethod.getReturnType().getParameterType();
        
        // ApiResponse 타입으로 이미 래핑된 경우는 건너뜀
        if (returnType.getName().contains("ApiResponse")) {
            return operation;
        }
        
        // 응답을 커스터마이징
        ApiResponses responses = operation.getResponses();
        if (responses != null) {
            responses.forEach((httpStatus, apiResponse) -> {
                Content content = apiResponse.getContent();
                if (content != null) {
                    content.forEach((mediaTypeKey, mediaType) -> {
                        Schema<?> originalSchema = mediaType.getSchema();
                        if (originalSchema != null) {
                            // ApiResponse 래퍼로 감싸기
                            Schema<?> wrappedSchema = createWrappedSchema(originalSchema, returnType, handlerMethod);
                            mediaType.setSchema(wrappedSchema);
                        }
                    });
                }
            });
        }
        
        return operation;
    }
    
    private Schema<?> createWrappedSchema(Schema<?> originalSchema, Class<?> returnType, HandlerMethod handlerMethod) {
        ObjectSchema wrapperSchema = new ObjectSchema();
        
        // Page 타입인 경우
        if (Page.class.isAssignableFrom(returnType)) {
            // Page<T>의 제네릭 타입(CompanyDTO) 추출
            Type genericReturnType = handlerMethod.getReturnType().getGenericParameterType();
            Schema<?> itemSchema = null;
            
            if (genericReturnType instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) genericReturnType;
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    // Page<CompanyDTO>에서 CompanyDTO 추출
                    Class<?> itemClass = (Class<?>) typeArgs[0];
                    // CompanyDTO 스키마를 직접 참조
                    itemSchema = new Schema<>().$ref("#/components/schemas/" + itemClass.getSimpleName());
                }
            }
            
            // data를 배열로 설정
            ArraySchema arraySchema = new ArraySchema();
            if (itemSchema != null) {
                arraySchema.items(itemSchema);
            }
            wrapperSchema.addProperty("data", arraySchema);
            
            // page 필드 추가
            ObjectSchema pageSchema = new ObjectSchema();
            pageSchema.addProperty("currentPage", new Schema<>().type("integer").example(1));
            pageSchema.addProperty("totalElement", new Schema<>().type("integer").example(3));
            pageSchema.addProperty("size", new Schema<>().type("integer").example(5));
            pageSchema.addProperty("totalPages", new Schema<>().type("integer").example(1));
            wrapperSchema.addProperty("page", pageSchema);
        } else {
            // 일반 타입 (단일 객체 또는 List)
            wrapperSchema.addProperty("data", originalSchema);
            wrapperSchema.addProperty("page", new Schema<>().type("object").nullable(true).example(null));
        }
        
        return wrapperSchema;
    }
}
