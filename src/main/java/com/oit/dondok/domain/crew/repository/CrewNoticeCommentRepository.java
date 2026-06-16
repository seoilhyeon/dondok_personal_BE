package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.CrewNoticeComment;
import com.oit.dondok.domain.crew.entity.CrewNoticeCommentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewNoticeCommentRepository extends JpaRepository<CrewNoticeComment, Long> {

  @EntityGraph(attributePaths = {"member"})
  List<CrewNoticeComment> findByCrewNoticeIdAndStatusOrderByCreatedAtAscIdAsc(
      Long crewNoticeId, CrewNoticeCommentStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"member"})
  @Query(
      "SELECT c FROM CrewNoticeComment c WHERE c.crewNotice.id = :noticeId"
          + " AND c.status = :status"
          + " AND (c.createdAt > :cursorCreatedAt"
          + "   OR (c.createdAt = :cursorCreatedAt AND c.id > :cursorId))"
          + " ORDER BY c.createdAt ASC, c.id ASC")
  List<CrewNoticeComment> findAfterCursor(
      @Param("noticeId") Long noticeId,
      @Param("status") CrewNoticeCommentStatus status,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);
}
