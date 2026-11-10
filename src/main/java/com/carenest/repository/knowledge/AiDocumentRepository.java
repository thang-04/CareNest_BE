package com.carenest.repository.knowledge;

import com.carenest.entity.knowledge.AiDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiDocumentRepository extends JpaRepository<AiDocument, Long> {

    List<AiDocument> findAllByOrderByCreatedAtDesc();
}
