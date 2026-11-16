package com.example.practica.note;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notes")
public record Note(@Id String id, String text, Instant createdAt) {
}
