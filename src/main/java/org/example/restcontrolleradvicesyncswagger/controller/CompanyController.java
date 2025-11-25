package org.example.restcontrolleradvicesyncswagger.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.restcontrolleradvicesyncswagger.dto.CompanyDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Company", description = "회사 정보 API")
@RestController
@RequestMapping("/company")
public class CompanyController {

    /**
     * 단건 조회
     * GET /company/1
     */
    @GetMapping(value = "/{id}")
    public CompanyDTO getCompany(
            @Parameter(description = "회사 ID", required = true, example = "1")
            @PathVariable Long id) {
        CompanyDTO dto = new CompanyDTO(id, "회사" + id, "주소" + id);

        return dto;

    }

    /**
     * 리스트 조회
     * GET /company/list
     */
    @GetMapping("/list")
    public List<CompanyDTO> getCompanyList() {

        List<CompanyDTO> list = List.of(
                new CompanyDTO(1L, "회사1", "주소1"),
                new CompanyDTO(2L, "회사2", "주소2"),
                new CompanyDTO(3L, "회사3", "주소3")
        );

        return list;
    }

    /**
     * 페이지 조회 (Page<CompanyDTO>)
     * GET /company/page?page=0&size=10
     */
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
}
