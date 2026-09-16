package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities.AdvanceRecord
import java.util.UUID

interface AdvanceRecordRepository : JpaRepository<AdvanceRecord, UUID> {
  fun getAdvanceRecordsByLegacyPaymentProfileId(legacyPaymentProfileId: String): AdvanceRecord?
}
