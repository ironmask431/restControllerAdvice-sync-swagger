package org.example.restcontrolleradvicesyncswagger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "API 공통 응답 포맷")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    @Schema(description = "응답 데이터")
    private T data;
    
    @Schema(description = "페이징 정보")
    private PageInfo page;
    
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }
    
    public static <T> ApiResponse<T> of(T data, PageInfo pageInfo) {
        return new ApiResponse<>(data, pageInfo);
    }
}
