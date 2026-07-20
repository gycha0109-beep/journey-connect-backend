package com.jc.backend.post;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PostModerationStatusConverter implements AttributeConverter<PostModerationStatus, String> {
    @Override
    public String convertToDatabaseColumn(PostModerationStatus attribute) {
        return attribute == null ? null : attribute.databaseValue();
    }

    @Override
    public PostModerationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PostModerationStatus.fromDatabase(dbData);
    }
}
