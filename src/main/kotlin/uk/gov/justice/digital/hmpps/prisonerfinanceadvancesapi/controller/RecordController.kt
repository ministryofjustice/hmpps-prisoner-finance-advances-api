package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.RecordService
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.utils.VALIDATION_MESSAGE_PRISON_NUMBER
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.utils.VALIDATION_REGEX_PRISON_NUMBER
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = "Advance Record Controller")
@RestController
class RecordController(val recordService: RecordService) {

  @Operation(
    summary = "Create a new advance record",
    description = "Creates a new advance record for a prisoner's spends account.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Advance record Created",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AdvanceRecordResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized - requires a valid OAuth2 token",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires an appropriate role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__ADVANCES__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__ADVANCES__RW')")
  @PostMapping("/advances")
  fun postAdvanceRecord(@Valid @RequestBody request: CreateAdvanceRecordRequest): ResponseEntity<AdvanceRecordResponse> {
    val createdRecordResponse = recordService.createAdvanceRecord(request)
    return ResponseEntity.status(201).body(createdRecordResponse)
  }

  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__ADVANCES__RW, ROLE_PRISONER_FINANCE__ADVANCES__RO])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__ADVANCES__RW', '$ROLE_PRISONER_FINANCE__ADVANCES__RO')")
  @GetMapping("/advances/{prisonNumber}")
  fun getAdvances(
    @Pattern(
      regexp = VALIDATION_REGEX_PRISON_NUMBER,
      message = VALIDATION_MESSAGE_PRISON_NUMBER,
    )
    @PathVariable("prisonNumber") prisonNumber: String,
    @RequestParam @Min(1) pageNumber: Int = 1,
    @RequestParam @Min(1) pageSize: Int = 25,
  ) : ResponseEntity<PagedResponse<AdvanceRecordResponse>> {

    val returnedAdvances = recordService.getAdvances(
      prisonNumber,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )

    return ResponseEntity.status(HttpStatus.OK).body(returnedAdvances)
  }
}
