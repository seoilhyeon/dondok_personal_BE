package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class CrewNoticeReactionWriter {

  private final CrewNoticeReactionRepository crewNoticeReactionRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveIgnoreDuplicate(CrewNoticeReaction reaction) {
    try {
      crewNoticeReactionRepository.saveAndFlush(reaction);
    } catch (DataIntegrityViolationException ignored) {
    }
  }
}
