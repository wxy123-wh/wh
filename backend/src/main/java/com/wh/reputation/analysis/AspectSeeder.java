package com.wh.reputation.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wh.reputation.persistence.AspectEntity;
import com.wh.reputation.persistence.AspectRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;

@Component
public class AspectSeeder implements ApplicationRunner {
    private final AspectRepository aspectRepository;
    private final ObjectMapper objectMapper;

    public AspectSeeder(AspectRepository aspectRepository, ObjectMapper objectMapper) {
        this.aspectRepository = aspectRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (aspectRepository.count() > 0) {
            return;
        }

        var path = DataFileLocator.resolveRequired("aspects.json");
        JsonNode root;
        try (var in = Files.newInputStream(path)) {
            root = objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load aspects: " + path, e);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalStateException("invalid aspects.json, expected array: " + path);
        }

        for (JsonNode node : root) {
            String name = node.path("name").asText(null);
            JsonNode keywords = node.get("keywords");
            if (name == null || name.isBlank() || keywords == null || !keywords.isArray()) {
                throw new IllegalStateException("invalid aspects.json item: " + node);
            }

            String keywordsJson;
            try {
                keywordsJson = objectMapper.writeValueAsString(keywords);
            } catch (IOException e) {
                throw new IllegalStateException("failed to serialize keywords_json for aspect: " + name, e);
            }

            aspectRepository.save(new AspectEntity(name, keywordsJson, LocalDateTime.now()));
        }
    }
}

