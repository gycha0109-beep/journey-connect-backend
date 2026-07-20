package com.jc.backend.post;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PostVisibilityConverter implements AttributeConverter<PostVisibility, String> {
    @Override
    public String convertToDatabaseColumn(PostVisibility attribute) {
        return attribute == null ? null : attribute.databaseValue();
    }

    @Override
    public PostVisibility convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PostVisibility.fromDatabase(dbData);
    }
}
