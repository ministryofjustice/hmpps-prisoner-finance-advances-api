package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceWriteOffEntity
import java.time.Instant
import java.util.UUID

interface AdvanceWriteOffRepository : JpaRepository<AdvanceWriteOffEntity, UUID> {
  fun findByAdvanceRecordId(advanceRecordId: UUID): List<AdvanceWriteOffEntity>

  fun findByAdvanceRecordIdAndCreatedAtAfter(advanceRecordId: UUID, timestamp: Instant): List<AdvanceWriteOffEntity>
}
