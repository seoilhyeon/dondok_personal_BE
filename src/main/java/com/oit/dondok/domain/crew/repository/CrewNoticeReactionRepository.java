package com.oit.dondok.domain.crew.repository;

import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrewNoticeReactionRepository extends JpaRepository<CrewNoticeReaction, Long> {

  List<CrewNoticeReaction> findByCrewNoticeId(Long crewNoticeId);

  Optional<CrewNoticeReaction> findByCrewNoticeIdAndMemberIdAndReactionType(
      Long crewNoticeId, Long memberId, String reactionType);

  boolean existsByCrewNoticeIdAndMemberIdAndReactionType(
      Long crewNoticeId, Long memberId, String reactionType);
}
