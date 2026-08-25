package com.jc.backend.crew;

import java.time.Instant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Records approved crew joins inside the existing APP transaction without switching database roles. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public final class CrewRecommendationFeedbackAspect {

    private final CrewMemberRepository members;
    private final CrewRecommendationFeedbackService feedback;

    public CrewRecommendationFeedbackAspect(
            CrewMemberRepository members,
            CrewRecommendationFeedbackService feedback) {
        this.members = members;
        this.feedback = feedback;
    }

    @Around("execution(* com.jc.backend.crew.CrewService.join(..))")
    public Object recordAutoApprovedJoin(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        Long userId = (Long) args[0];
        Long crewId = (Long) args[1];
        CrewMemberStatus before = members.findByCrewIdAndUserId(crewId, userId)
                .map(CrewMember::getStatus)
                .orElse(null);

        Object result = joinPoint.proceed();
        if (result instanceof CrewDtos.ApplicationView application
                && application.status() == CrewMemberStatus.APPROVED
                && before != CrewMemberStatus.APPROVED) {
            record(application);
        }
        return result;
    }

    @Around("execution(* com.jc.backend.crew.CrewService.review(..))")
    public Object recordOwnerApprovedJoin(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof CrewDtos.ApplicationView application
                && application.status() == CrewMemberStatus.APPROVED) {
            record(application);
        }
        return result;
    }

    private void record(CrewDtos.ApplicationView application) {
        Instant approvedAt = application.reviewedAt();
        if (approvedAt == null) {
            throw new IllegalStateException("Approved crew membership must have reviewedAt");
        }
        members.flush();
        feedback.recordApprovedJoin(application.userId(), application.crewId(), approvedAt);
    }
}
