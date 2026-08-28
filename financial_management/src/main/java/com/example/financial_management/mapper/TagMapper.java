package com.example.financial_management.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import com.example.financial_management.entity.Tag;
import com.example.financial_management.model.tag.TagRequest;
import com.example.financial_management.model.tag.TagResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TagMapper {

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Tag toEntity(TagRequest request, UUID userId);

    TagResponse toResponse(Tag entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntity(TagRequest request, @MappingTarget Tag entity);
}
