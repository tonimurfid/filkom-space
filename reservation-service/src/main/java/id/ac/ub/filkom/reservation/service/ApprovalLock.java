package id.ac.ub.filkom.reservation.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Serializes concurrent decisions for the same room and date across all instances.
// PostgreSQL advisory locks live for the enclosing Spring transaction.
// Disabled in H2-only tests; enabled for the PostgreSQL Compose deployment.
@Component
public class ApprovalLock {
  private final EntityManager entityManager;
  private final boolean enabled;

  public ApprovalLock(
      EntityManager entityManager,
      @Value("${app.approval-pg-locks:true}") boolean enabled) {
    this.entityManager = entityManager;
    this.enabled = enabled;
  }

  public void acquire(UUID roomId, LocalDate date) {
    if (!enabled) return;
    entityManager.createNativeQuery("select pg_advisory_xact_lock(?1, ?2)")
        .setParameter(1, roomId.toString().hashCode())
        .setParameter(2, date.toString().hashCode())
        .getSingleResult();
  }
}
