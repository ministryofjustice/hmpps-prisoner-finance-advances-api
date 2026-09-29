package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.helpers

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.IntegrationTestBase.Companion.setIdempotencyKey
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvancePaymentRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Instant
import java.util.UUID

@ExtendWith(GeneralLedgerApiExtension::class, HmppsAuthApiExtension::class)
@TestConfiguration
class IntegrationTestHelpers(
  @Autowired
  private val advanceRecordRepository: AdvanceRecordRepository,
  private val advancePaymentRepository: AdvancePaymentRepository,
  private val jwtAuthHelper: JwtAuthorisationHelper,
) {

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  lateinit var webTestClient: WebTestClient

  fun setWebClient(webClient: WebTestClient) {
    webTestClient = webClient
  }

  @Autowired
  lateinit var entityManager: EntityManager

  fun createAdvance(
    prisonNumber: String,
    legacyPaymentProfileId: Long = 1234,
    legacyInformationNumber: String = "5678",
    amount: Long,
    prisonId: String = "LEI",
    repaymentAmount: Long,
    status: AdvanceStatus = AdvanceStatus.ACTIVE,
    prisonerSubAccountId: UUID,
    prisonSubAccountId: UUID,
  ): AdvanceRecordResponse {
    val advanceRecordRequest = CreateAdvanceRecordRequest(
      legacyPaymentProfileId = legacyPaymentProfileId,
      legacyInformationNumber = legacyInformationNumber,
      prisonNumber = prisonNumber,
      prisonID = prisonId,
      amount = amount,
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = repaymentAmount,
      reference = "REF",
      createdBy = "USER",
      status = status,
      prisonerSubAccountId = prisonerSubAccountId,
      prisonSubAccountId = prisonSubAccountId,
    )

    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = prisonerSubAccountId.toString(),
      debtorSubAccountUuid = prisonSubAccountId.toString(),
      reference = advanceRecordRequest.reference,
      returnUUID = UUID.randomUUID(),
      amount = advanceRecordRequest.amount,
      legacyTransactionId = advanceRecordRequest.legacyTransactionId?.toString(),
    )

    val responseBody = webTestClient.post().uri("/advances")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(advanceRecordRequest)
      .exchange()
      .expectStatus()
      .isCreated
      .expectBody<AdvanceRecordResponse>()
      .returnResult()
      .responseBody!!

    return responseBody
  }

  @Transactional
  fun clearDB() {
    entityManager.clear()
    entityManager.flush()
    advancePaymentRepository.deleteAll()
    advanceRecordRepository.deleteAll()
  }
}
