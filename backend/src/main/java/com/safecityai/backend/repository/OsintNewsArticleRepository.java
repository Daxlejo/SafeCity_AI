package com.safecityai.backend.repository;

import com.safecityai.backend.model.OsintNewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OsintNewsArticleRepository extends JpaRepository<OsintNewsArticle, Long> {

    boolean existsByContentHash(String contentHash);

    Page<OsintNewsArticle> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
