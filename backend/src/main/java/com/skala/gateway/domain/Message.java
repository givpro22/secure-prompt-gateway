package com.skala.gateway.domain;

import com.skala.gateway.domain.enums.MessageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 직원이 제출한 프롬프트 (기획서 6.2, 7.5).
 *
 * <p>원문과 전송본이 분리되어 있다. {@code originalText}는 감사 목적으로만 보관하며
 * <b>어떤 API 응답에도 싣지 않는다</b> (6.2 "원문. 화면 미노출", 계약서 §2).
 * 외부로 나가는 것은 {@code submittedText}뿐이다 (9.3).
 *
 * <p>{@code submittedText}가 {@code null}이면 미전송이다. BLOCK 판정이면 마스킹 자체를
 * 실행하지 않으므로 (0.5 D5) 대상 텍스트가 없다.
 */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "original_text", nullable = false, columnDefinition = "text")
    private String originalText;

    @Column(name = "submitted_text", columnDefinition = "text")
    private String submittedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Message() {
    }

    public Message(AppUser user, String originalText, String submittedText, MessageStatus status) {
        this.user = user;
        this.originalText = originalText;
        this.submittedText = submittedText;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getMessageId() {
        return messageId;
    }

    public AppUser getUser() {
        return user;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getSubmittedText() {
        return submittedText;
    }

    public void setSubmittedText(String submittedText) {
        this.submittedText = submittedText;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
