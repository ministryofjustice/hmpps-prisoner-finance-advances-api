package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request

import java.time.Instant

data class AdvanceWriteOffRequest(
  val amount : Long,
  val writeOffDateTime : Instant,
  val writtenOffBy : String
)