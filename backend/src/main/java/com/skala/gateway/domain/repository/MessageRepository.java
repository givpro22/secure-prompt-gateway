package com.skala.gateway.domain.repository;

import com.skala.gateway.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
}
