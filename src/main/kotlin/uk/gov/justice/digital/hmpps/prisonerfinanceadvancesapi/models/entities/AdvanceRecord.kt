package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "advance_records")
class AdvanceRecord(

  @Id
  var id: UUID = UUID.randomUUID(),

  @Column(name = "legacy_payment_profile_id", nullable = false, unique = true)
  var legacyPaymentProfileId: String,

  @Column(name = "legacy_information_number", nullable = false, unique = false)
  var legacyInformationNumber: String,

  @Column(name = "prison_number", nullable = false, unique = false)
  var prisonNumber: String,

  @Column(name = "prison_id", nullable = false, unique = false)
  var prisonID: String,

  @Column(name = "amount", nullable = false, unique = false)
  var amount: Int,

  @Column(name = "created_on", nullable = false, unique = false)
  var createdOn: Instant,

  @Column(name = "repayment_start_date", nullable = false, unique = false)
  var repaymentStartDate: Instant,

  @Column(name = "repayment_amount", nullable = false, unique = false)
  var repaymentAmount: Int,

  @Column(name = "reference", nullable = false, unique = false)
  var reference: String,

  @Column(name = "created_by", nullable = false, unique = false)
  var createdBy: String,

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, unique = false)
  var status: AdvanceStatus,

)
