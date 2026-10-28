package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvancePaymentEntity
import java.time.Instant
import java.util.UUID

interface AdvancePaymentRepository : JpaRepository<AdvancePaymentEntity, UUID> {

  fun findByTransactionId(transactionId: UUID): AdvancePaymentEntity?

  fun findAdvancePaymentEntitiesByAdvanceRecordId(advanceRecordId: UUID): List<AdvancePaymentEntity>

  fun findAdvancePaymentEntitiesByAdvanceRecordIdAndPrisonerPostingType(
    advanceRecordId: UUID,
    prisonerPostingType: PostingType,
    pageable: Pageable,
  ): Page<AdvancePaymentEntity>

  fun findByAdvanceRecordIdAndTimestampAfter(advanceRecordId: UUID, timestamp: Instant): List<AdvancePaymentEntity>
}
