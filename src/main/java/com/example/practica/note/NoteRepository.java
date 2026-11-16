package com.example.practica.note;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface NoteRepository extends ReactiveMongoRepository<Note, String> {
}
