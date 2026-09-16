package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.helpers

import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.request.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.repositories.AdvanceRecordRepository
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Instant

@TestConfiguration
class IntegrationTestHelpers(
  @Autowired
  private val advanceRecordRepository: AdvanceRecordRepository,
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
    legacyPaymentProfileId: String = "1234",
    legacyInformationNumber: String = "5678",
    amount: Int,
    prisonId: String = "LEI",
    repaymentAmount: Int,
    status: AdvanceStatus = AdvanceStatus.ACTIVE,
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
    )

    val responseBody = webTestClient.post().uri("/advances")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
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
    advanceRecordRepository.deleteAll()
  }
}
