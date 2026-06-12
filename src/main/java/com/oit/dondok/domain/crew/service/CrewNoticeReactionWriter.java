package com.oit.dondok.domain.crew.service;

import com.oit.dondok.domain.crew.entity.CrewNoticeReaction;
import com.oit.dondok.domain.crew.repository.CrewNoticeReactionRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class CrewNoticeReactionWriter {

  private static final String UK_REACTION = "uk_crew_notice_reaction_notice_member_type";

  private final CrewNoticeReactionRepository crewNoticeReactionRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveIgnoreDuplicate(CrewNoticeReaction reaction) {
    try {
      crewNoticeReactionRepository.saveAndFlush(reaction);
    } catch (DataIntegrityViolationException exception) {
      Throwable cause = exception;
      while (cause != null) {
        if (cause instanceof ConstraintViolationException constraintException) {
          if (UK_REACTION.equalsIgnoreCase(constraintException.getConstraintName())) {
            return;
          }
          break;
        }
        cause = cause.getCause();
      }
      throw exception;
    }
  }
}
