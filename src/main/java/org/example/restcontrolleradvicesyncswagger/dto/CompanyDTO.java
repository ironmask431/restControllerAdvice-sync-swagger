package org.example.restcontrolleradvicesyncswagger.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "회사 정보 DTO")
@Getter
@NoArgsConstructor
public class CompanyDTO {
    
    @Schema(description = "회사 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long  id;
    
    @Schema(description = "회사명", example = "삼성전자", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String name;
    
    @Schema(description = "회사 주소", example = "서울시 강남구")
    private String address;

    public CompanyDTO(Long id, String name, String address) {
        this.id = id;
        this.name = name;
        this.address = address;
    }
}
