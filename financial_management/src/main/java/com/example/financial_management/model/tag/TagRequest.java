package com.example.financial_management.model.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagRequest {

    @NotBlank(message = "Tên tag không được để trống")
    @Size(max = 100, message = "Tên tag tối đa 100 ký tự")
    private String name;

    @Size(max = 30, message = "Mã màu tối đa 30 ký tự")
    private String color;
}
