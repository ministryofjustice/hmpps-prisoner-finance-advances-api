package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services.RecordService
import java.util.UUID

@Tag(name = "Advance Repayment Controller")
@RestController
class AdvanceRepaymentController(
  val recordService: RecordService,
) {

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
    @PathVariable("advanceId") advanceId: UUID,
  ): ResponseEntity<AdvanceRepaymentResponse> = ResponseEntity.ok().body(
    recordService.repayAdvance(
      createAdvanceRepaymentRequest,
      advanceId,
      idempotencyKey,
    ),
  )
}
