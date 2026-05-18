package com.safecityai.backend.repository;

import com.safecityai.backend.model.OsintNewsArticle;
import com.safecityai.backend.model.enums.OsintArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OsintNewsArticleRepository extends JpaRepository<OsintNewsArticle, Long> {

    boolean existsByContentHash(String contentHash);

    /** Feed público: solo artículos visibles, más recientes primero */
    Page<OsintNewsArticle> findByStatusOrderByCreatedAtDesc(OsintArticleStatus status, Pageable pageable);

    /** Panel admin: todos los artículos independientemente del status */
    Page<OsintNewsArticle> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
