package com.example.orderquery.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Denormalized order view. One doc per order, upserted by the projector when
 * events arrive on `order.created` (and later: payment / status change topics).
 *
 * Indexed with suffix _v1 — on breaking mapping changes, create _v2 index,
 * rebuild from the event stream, flip the alias.
 */
@Document(indexName = "orders_v1")
public class OrderDoc {

    @Id
    private String orderId;

    @Field(type = FieldType.Long)
    private Long productId;

    @Field(type = FieldType.Integer)
    private Integer quantity;

    /** Elasticsearch has no decimal type — store as scaled_float via string. */
    @Field(type = FieldType.Keyword)
    private String amount;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;

    public OrderDoc() {}

    public OrderDoc(String orderId, Long productId, Integer quantity, BigDecimal amount,
                    String status, Instant createdAt, Instant updatedAt) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount != null ? amount.toPlainString() : null;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public String getAmount() { return amount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setAmount(String amount) { this.amount = amount; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
