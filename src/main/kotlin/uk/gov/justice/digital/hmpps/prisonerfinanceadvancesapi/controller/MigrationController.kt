package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.AdvanceMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.RecordService
import java.time.Instant
import java.util.UUID

@Tag(name = "Migration Controller")
@RestController
class MigrationController(
  val recordService: RecordService,
) {

  @Operation(
    summary = "Create a new advance record",
    description = "Creates a new advance record for a prisoner's spends account.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "501",
        description = "Not implemented",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = uk.gov.justice.hmpps.kotlin.common.ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__ADVANCES__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__ADVANCES__RW')")
  @PostMapping("/migrate/advances")
  fun migrateAdvance(
    @Valid @RequestBody request: AdvanceMigrationRequest,
  ): ResponseEntity<AdvanceRecordResponse> {
    val response = AdvanceRecordResponse(
      // TODO: this is an advanceRecord Id - Not the migration id
      id = UUID.randomUUID(),
      legacyPaymentProfileId = request.legacyPaymentProfileId,
      legacyInformationNumber = request.legacyInformationNumber,
      prisonNumber = request.prisonNumber,
      prisonID = request.prisonID,
      amount = request.amountIssued,
      createdOn = request.createdOn,
      repaymentStartDate = request.repaymentStartDate,
      repaymentAmount = request.repaymentAmount,
      reference = request.reference,
      comment = request.comment,
      createdBy = request.createdBy,
      status = request.status,
      updatedAt = Instant.now(),
      generalLedgerUUID = null,
    )

    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }
}
