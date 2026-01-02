CREATE TABLE IF NOT EXISTS platform (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) UNIQUE NOT NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  brand VARCHAR(64) NULL,
  model VARCHAR(64) NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  review_id_raw VARCHAR(128) NULL,
  rating INT NULL,
  content_raw TEXT NOT NULL,
  content_clean TEXT NOT NULL,
  review_time DATETIME NULL,
  like_count INT NULL,
  hash VARCHAR(64) NOT NULL UNIQUE,
  overall_sentiment_label VARCHAR(8) NOT NULL DEFAULT 'NEU',
  overall_sentiment_score DOUBLE NOT NULL DEFAULT 0.0,
  created_at DATETIME NOT NULL,
  INDEX idx_review_product_time (product_id, review_time),
  INDEX idx_review_platform (platform_id),
  INDEX idx_review_overall_sent (overall_sentiment_label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS aspect (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(32) UNIQUE NOT NULL,
  keywords_json JSON NOT NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS review_aspect_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  review_id BIGINT NOT NULL,
  aspect_id BIGINT NOT NULL,
  hit_keywords_json JSON NOT NULL,
  sentiment_label VARCHAR(8) NOT NULL,
  sentiment_score DOUBLE NOT NULL DEFAULT 0.0,
  confidence DOUBLE NOT NULL DEFAULT 0.0,
  created_at DATETIME NOT NULL,
  INDEX idx_rar_review (review_id),
  INDEX idx_rar_aspect (aspect_id),
  INDEX idx_rar_aspect_sent (aspect_id, sentiment_label),
  CONSTRAINT fk_rar_review FOREIGN KEY (review_id) REFERENCES review(id),
  CONSTRAINT fk_rar_aspect FOREIGN KEY (aspect_id) REFERENCES aspect(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

