package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "advance_write_off")
data class AdvanceWriteOffEntity(

  @Id
  var id: UUID = UUID.randomUUID(),

  @Column(name = "advance_record_id", nullable = false, unique = false)
  var advanceRecordId: UUID,

  @Column(name = "amount", nullable = false, unique = false)
  var amount: Long,

  @Column(name = "created_by", nullable = false, unique = false)
  var createdBy: String,

  @Column(name = "created_at", nullable = false, unique = false)
  var createdAt: Instant,

)
