package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request

import io.swagger.v3.oas.annotations.media.Schema

class AdvanceUpdateRequest(

  @field:Schema(description = "Update the comment on the Advance Record")
  val comment: String,
  @field:Schema(description = "Last user that updated the Advance Record")
  val updatedBy: String,
)
