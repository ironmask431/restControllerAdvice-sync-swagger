package org.example.restcontrolleradvicesyncswagger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Schema(description = "페이징 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {
    
    @Schema(description = "현재 페이지 번호", example = "1")
    private int currentPage;
    
    @Schema(description = "전체 요소 개수", example = "3")
    private long totalElement;
    
    @Schema(description = "페이지 크기", example = "5")
    private int size;
    
    @Schema(description = "전체 페이지 수", example = "1")
    private int totalPages;
    
    public static PageInfo from(Page<?> page) {
        return new PageInfo(
                page.getNumber() + 1,  // 0-based를 1-based로 변경
                page.getTotalElements(),
                page.getSize(),
                page.getTotalPages()
        );
    }
}
