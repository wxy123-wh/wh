package com.wh.reputation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "aspect")
public class AspectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    @Column(name = "keywords_json", nullable = false, columnDefinition = "json")
    private String keywordsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AspectEntity() {}

    public AspectEntity(String name, String keywordsJson, LocalDateTime createdAt) {
        this.name = name;
        this.keywordsJson = keywordsJson;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getKeywordsJson() {
        return keywordsJson;
    }
}
