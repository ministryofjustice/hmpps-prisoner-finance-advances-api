package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvancePaymentEntity
import java.util.UUID

interface AdvancePaymentRepository : JpaRepository<AdvancePaymentEntity, UUID> {

  fun findByTransactionId(transactionId: UUID): AdvancePaymentEntity?
}
