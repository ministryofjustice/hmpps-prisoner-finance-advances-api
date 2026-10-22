package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities

import jakarta.persistence.Column
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.Instant
import java.util.UUID

class AdvanceBalanceMigrationEntity(

  @Id
  var id: UUID = UUID.randomUUID(),

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "advance_record_id", nullable = false)
  var advanceRecord: AdvanceRecordEntity,

  @Column(name = "amount_outstanding", nullable = false, unique = false)
  var amount_outstanding: Long,

  @Column(name = "timestamp", nullable = false, unique = false)
  var timestamp: Instant,
)
