package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.IntegrationTestBase.Companion.setIdempotencyKey
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.AdvanceMigrationRequest
import java.time.Instant
import java.util.UUID

class AdvanceMigrationTest : IntegrationTestBase() {
  val migrationRequest = AdvanceMigrationRequest(
    legacyPaymentProfileId = 1111,
    legacyInformationNumber = "1234-1",
    prisonNumber = "A134ABC",
    prisonID = "LEI",
    amountIssued = 100L,
    amountOutstanding = 80L,
    createdOn = Instant.now(),
    migrationTimestamp = Instant.now(),
    repaymentStartDate = Instant.now(),
    repaymentAmount = 10L,
    reference = "",
    comment = "",
    createdBy = "TEST",
    status = AdvanceStatus.ACTIVE,
    legacyTransactionId = 12345,
  )

  @Test
  fun `Should return 501 not implemented`() {
    webTestClient.post().uri("/migrate/advances")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(migrationRequest)
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
      .expectBody<ErrorResponse>()
  }

  @Test
  fun `should return 403 forbidden when using the incorrect role`() {
    webTestClient.post().uri("/migrate/advances")
      .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(migrationRequest)
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatus.FORBIDDEN)
      .expectBody<ErrorResponse>()
  }
}
