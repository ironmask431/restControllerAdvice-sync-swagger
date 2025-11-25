package org.example.restcontrolleradvicesyncswagger.advice;

import org.example.restcontrolleradvicesyncswagger.dto.ApiResponse;
import org.example.restcontrolleradvicesyncswagger.dto.PageInfo;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "org.example.restcontrolleradvicesyncswagger.controller")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // ApiResponse로 이미 감싸진 응답은 제외
        return !returnType.getParameterType().equals(ApiResponse.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, 
                                  MethodParameter returnType, 
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType, 
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        
        // Swagger UI 관련 요청은 변환하지 않음
        String path = request.getURI().getPath();
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            return body;
        }
        
        // Page 객체인 경우
        if (body instanceof Page<?> page) {
            PageInfo pageInfo = PageInfo.from(page);
            return ApiResponse.of(page.getContent(), pageInfo);
        }
        
        // 일반 객체인 경우
        return ApiResponse.of(body);
    }
}
