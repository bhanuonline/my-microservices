-- Baseline schema captured from the running DB.
-- Produced via `mysqldump --no-data productdb` after Hibernate ddl-auto=update
-- had generated the tables from the entity classes. From here on, every entity
-- change needs its own V2+, V3+... migration — ddl-auto is set to validate.

CREATE TABLE `attribute` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_hpwum0iq12fs4ej5d0tgy6wsn` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2y94svpmqttx80mshyny85wqr` (`parent_id`),
  CONSTRAINT `FK2y94svpmqttx80mshyny85wqr` FOREIGN KEY (`parent_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `attribute_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `value` varchar(255) NOT NULL,
  `attribute_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK59xqw12tl928rqcdu2h9o6mau` (`attribute_id`),
  CONSTRAINT `FK59xqw12tl928rqcdu2h9o6mau` FOREIGN KEY (`attribute_id`) REFERENCES `attribute` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `base_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `base_code` varchar(255) NOT NULL,
  `brand` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `category_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_doxt4osmrr9gqqrs047drip03` (`base_code`),
  KEY `FKcxgqv1ue2ysxyj2uahp5fpma3` (`category_id`),
  CONSTRAINT `FKcxgqv1ue2ysxyj2uahp5fpma3` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_variant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `price` double NOT NULL,
  `quantity_in_stock` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `base_product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_l7iu9lfo6errb2odphb6yfmmv` (`code`),
  KEY `FK9m7ljcohrh15yjqp6v34tvlg2` (`base_product_id`),
  CONSTRAINT `FK9m7ljcohrh15yjqp6v34tvlg2` FOREIGN KEY (`base_product_id`) REFERENCES `base_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `variant_attribute` (
  `variant_id` bigint NOT NULL,
  `attribute_value_id` bigint NOT NULL,
  KEY `FK87xhxywaoh6x50dwq2htmcck6` (`attribute_value_id`),
  KEY `FKftpvthf91cl81y9yu9s365ies` (`variant_id`),
  CONSTRAINT `FK87xhxywaoh6x50dwq2htmcck6` FOREIGN KEY (`attribute_value_id`) REFERENCES `attribute_value` (`id`),
  CONSTRAINT `FKftpvthf91cl81y9yu9s365ies` FOREIGN KEY (`variant_id`) REFERENCES `product_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_level` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_updated` datetime(6) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `quantity_on_hand` int NOT NULL,
  `quantity_reserved` int NOT NULL,
  `product_variant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7yp7k78wew0mscw75v4gqoxcc` (`product_variant_id`),
  CONSTRAINT `FK7yp7k78wew0mscw75v4gqoxcc` FOREIGN KEY (`product_variant_id`) REFERENCES `product_variant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `brand` varchar(255) DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `dimensions` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `price` double NOT NULL,
  `quantity_in_stock` int NOT NULL,
  `sku` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `weight` double NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_o61fmio5yukmmiqgnxf8pnavn` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `outbox_events` (
  `id` binary(16) NOT NULL,
  `aggregate_type` varchar(50) NOT NULL,
  `attempt_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `destination` varchar(200) NOT NULL,
  `payload` tinytext NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('PENDING','SENT','FAILED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_outbox_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `processed_events` (
  `id` binary(16) NOT NULL,
  `consumer` varchar(100) NOT NULL,
  `processed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
