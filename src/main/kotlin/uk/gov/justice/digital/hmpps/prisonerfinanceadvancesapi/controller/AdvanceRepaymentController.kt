package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvancePrisonerRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.RecordService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.UUID

@Tag(name = "Advance Repayment Controller")
@RestController
class AdvanceRepaymentController(
  val recordService: RecordService,
) {
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Advance Repayment Created",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AdvanceRepaymentResponse::class))],
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
        responseCode = "404",
        description = "Not found - Advance not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "502",
        description = "Bad Gateway - A dependency returned an unexpected error",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__ADVANCES__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__ADVANCES__RW')")
  @PostMapping("/advances/{advanceId}/repay")
  fun repayAdvance(
    @Parameter(
      name = "Idempotency-Key",
      `in` = ParameterIn.HEADER,
      required = true,
      description = "An Idempotency Key to ensure that transactions are not repeated",
    )
    @RequestHeader(
      "Idempotency-Key",
      required = true,
    )
    @Valid idempotencyKey: UUID,
    @Valid @RequestBody createAdvanceRepaymentRequest: CreateAdvanceRepaymentRequest,
    @PathVariable advanceId: UUID,
  ): ResponseEntity<AdvanceRepaymentResponse> = ResponseEntity.status(201).body(
    recordService.repayAdvance(
      createAdvanceRepaymentRequest,
      advanceId,
      idempotencyKey,
    ),
  )

  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner repayments towards an advance",
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
        responseCode = "404",
        description = "Not found - Advance was not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__ADVANCES__RW, ROLE_PRISONER_FINANCE__ADVANCES__RO])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__ADVANCES__RW', '$ROLE_PRISONER_FINANCE__ADVANCES__RO')")
  @GetMapping("/advances/{advanceId}/repayments")
  fun getPrisonerRepayments(
    @PathVariable advanceId: UUID,
    @RequestParam @Min(1) pageNumber: Int = 1,
    @RequestParam @Min(1) pageSize: Int = 25,
  ): ResponseEntity<PagedResponse<AdvancePrisonerRepaymentResponse>> {
    val prisonerRepayments = recordService.getPrisonerRepayments(advanceId, pageNumber, pageSize)

    return ResponseEntity.ok().body(prisonerRepayments)
  }
}
