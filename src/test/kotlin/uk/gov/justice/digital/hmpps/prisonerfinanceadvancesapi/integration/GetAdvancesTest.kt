package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvancePrisonerRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.UUID

class GetAdvancesTest : IntegrationTestBase() {

  val prisonerParentAccountId = UUID.randomUUID()
  val prisonerSubAccountId = UUID.randomUUID()

  val prisonParentAccountId = UUID.randomUUID()
  val prisonSubAccountId = UUID.randomUUID()
  val prisonNumber = "X1234AB"
  val prisonId = "LEI"

  @BeforeEach
  fun setUp() {
    this.integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
    generalLedgerApi.resetAll()
  }

  @Nested
  inner class GetAdvances {
    @Test
    fun `should get all advances for prisonNumber`() {
      repeat(10) { i ->
        integrationTestHelpers.createAdvance(
          prisonNumber = prisonNumber,
          legacyPaymentProfileId = i.toLong(),
          legacyInformationNumber = i.toString(),
          amount = i.toLong(),
          prisonId = prisonId,
          repaymentAmount = 5,
          status = AdvanceStatus.ACTIVE,
          prisonerSubAccountId = prisonerSubAccountId,
          prisonSubAccountId = prisonSubAccountId,
          prisonParentAccountId = prisonParentAccountId,
          prisonerParentAccountId = prisonerParentAccountId,
        )
      }

      val response = webTestClient.get().uri("/advances/$prisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<AdvanceRecordResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(response.content).hasSize(10)
      val firstAdvance = response.content.first()

      assertThat(firstAdvance.prisonNumber).isEqualTo(prisonNumber)
      assertThat(firstAdvance.prisonID).isEqualTo(prisonId)
      assertThat(firstAdvance.legacyPaymentProfileId).isEqualTo(9)
      assertThat(firstAdvance.legacyInformationNumber).isEqualTo("9")
      assertThat(firstAdvance.status).isEqualTo(AdvanceStatus.ACTIVE)
      assertThat(firstAdvance.amount).isEqualTo(9)
      assertThat(firstAdvance.repaymentAmount).isEqualTo(5)

      assertThat(response.pageNumber).isEqualTo(1)
      assertThat(response.totalPages).isEqualTo(1)
      assertThat(response.totalElements).isEqualTo(10)
    }

    @Test
    fun `should get no advances for prisonNumber when there are none`() {
      val response = webTestClient.get().uri("/advances/$prisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<AdvanceRecordResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(response.content).hasSize(0)

      assertThat(response.pageNumber).isEqualTo(1)
      assertThat(response.totalPages).isEqualTo(0)
      assertThat(response.totalElements).isEqualTo(0)
    }

    @Test
    fun `should return 400 BAD REQUEST when page number is invalid`() {
      val responseBody = webTestClient.get().uri("/advances/$prisonNumber?pageNumber=ABC")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Parameter 'pageNumber' must be of type int")
    }

    @Test
    fun `should return 400 BAD REQUEST when page size is invalid`() {
      val responseBody = webTestClient.get().uri("/advances/$prisonNumber?pageSize=ABC")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Parameter 'pageSize' must be of type int")
    }

    @Test
    fun `should return 400 BAD REQUEST when prison number is invalid`() {
      val invalidPrisonNumber = "A123 45BC"

      webTestClient.get().uri("/advances/$invalidPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return 400 BAD REQUEST when requesting a page that doesnt exist`() {
      repeat(25) { i ->
        integrationTestHelpers.createAdvance(
          prisonNumber = prisonNumber,
          legacyPaymentProfileId = i.toLong(),
          legacyInformationNumber = i.toString(),
          amount = i.toLong(),
          prisonId = prisonId,
          repaymentAmount = 5,
          status = AdvanceStatus.ACTIVE,
          prisonerSubAccountId = prisonerSubAccountId,
          prisonSubAccountId = prisonSubAccountId,
          prisonerParentAccountId = prisonerParentAccountId,
          prisonParentAccountId = prisonParentAccountId,
        )
      }

      val responseBody = webTestClient.get().uri("/advances/$prisonNumber?pageNumber=3")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Page requested is out of range")
    }

    @Test
    fun `should get second page of results of advances for prison number`() {
      repeat(25) { i ->
        integrationTestHelpers.createAdvance(
          prisonNumber = prisonNumber,
          legacyPaymentProfileId = i.toLong(),
          legacyInformationNumber = i.toString(),
          amount = i.toLong(),
          prisonId = prisonId,
          repaymentAmount = 5,
          status = AdvanceStatus.ACTIVE,
          prisonerSubAccountId = prisonerSubAccountId,
          prisonSubAccountId = prisonSubAccountId,
          prisonParentAccountId = prisonParentAccountId,
          prisonerParentAccountId = prisonerParentAccountId,
        )
      }

      val responseBody = webTestClient.get().uri("/advances/$prisonNumber?pageSize=24&pageNumber=2")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<AdvanceRecordResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.content).hasSize(1)
      assertThat(responseBody.totalElements).isEqualTo(25)
      assertThat(responseBody.totalPages).isEqualTo(2)
      assertThat(responseBody.pageNumber).isEqualTo(2)
    }

    @Test
    fun `should return 403 forbidden when user does not have the correct role`() {
      webTestClient.get().uri("/advances/$prisonNumber")
        .headers(setAuthorisation(roles = listOf("INVALID_ROLE")))
        .exchange()
        .expectStatus()
        .isForbidden
    }
  }

  @Nested
  inner class GetAdvancePayments {

    lateinit var advance: AdvanceRecordResponse

    fun createAdvanceWithRepayments() {
      advance = integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
        legacyPaymentProfileId = 123,
        legacyInformationNumber = "3333",
        amount = 100,
        prisonId = prisonId,
        repaymentAmount = 5,
        status = AdvanceStatus.ACTIVE,
        prisonerSubAccountId = prisonerSubAccountId,
        prisonSubAccountId = prisonSubAccountId,
        prisonParentAccountId = prisonParentAccountId,
        prisonerParentAccountId = prisonerParentAccountId,
      )

      repeat(25) { i ->
        integrationTestHelpers.repayAdvance(
          advanceId = advance.id,
          repaymentAmount = 1,
          prisonSubAccountId = prisonSubAccountId,
          prisonerSubAccountId = prisonerSubAccountId,
        )
      }
    }

    @Test
    fun `should get no repayments for advance when there are none`() {
      val response = webTestClient.get().uri("/advances/${UUID.randomUUID()}/repayments")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<AdvancePrisonerRepaymentResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(response.content).hasSize(0)

      assertThat(response.pageNumber).isEqualTo(1)
      assertThat(response.totalPages).isEqualTo(0)
      assertThat(response.totalElements).isEqualTo(0)
    }

    @Test
    fun `should get all advance repayments for an advance`() {
      createAdvanceWithRepayments()

      val response = webTestClient.get().uri("/advances/${advance.id}/repayments")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RO)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<AdvancePrisonerRepaymentResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(response.content).hasSize(25)
      val firstPayment = response.content.first()

      assertThat(firstPayment.advanceId).isEqualTo(advance.id)
      assertThat(firstPayment.amount).isEqualTo(1)
      assertThat(firstPayment.createdBy).isEqualTo("TEST")

      assertThat(response.pageNumber).isEqualTo(1)
      assertThat(response.totalPages).isEqualTo(1)
      assertThat(response.totalElements).isEqualTo(25)
    }

    @Test
    fun `should get second page of results of repayments for advance`() {
      createAdvanceWithRepayments()

      val responseBody = webTestClient.get().uri("/advances/${advance.id}/repayments?pageSize=24&pageNumber=2")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<PagedResponse<AdvancePrisonerRepaymentResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.content).hasSize(1)
      assertThat(responseBody.totalElements).isEqualTo(25)
      assertThat(responseBody.totalPages).isEqualTo(2)
      assertThat(responseBody.pageNumber).isEqualTo(2)
    }

    @Test
    fun `should return 400 BAD REQUEST when page number is invalid`() {
      val responseBody = webTestClient.get().uri("/advances/${UUID.randomUUID()}/repayments?pageNumber=ABC")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Parameter 'pageNumber' must be of type int")
    }

    @Test
    fun `should return 400 BAD REQUEST when page size is invalid`() {
      val responseBody = webTestClient.get().uri("/advances/${UUID.randomUUID()}/repayments?pageSize=ABC")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Parameter 'pageSize' must be of type int")
    }

    @Test
    fun `should return 400 BAD REQUEST when advanceId is invalid`() {
      webTestClient.get().uri("/advances/INVALID/repayments")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
    }

    @Test
    fun `should return 400 BAD REQUEST when requesting a page that doesnt exist`() {
      createAdvanceWithRepayments()

      val responseBody = webTestClient.get().uri("/advances/${advance.id}/repayments?pageNumber=99")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
        .exchange()
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Page requested is out of range")
    }

    @Test
    fun `should return 403 forbidden when user does not have the correct role`() {
      webTestClient.get().uri("/advances/${UUID.randomUUID()}/repayments")
        .headers(setAuthorisation(roles = listOf("INVALID_ROLE")))
        .exchange()
        .expectStatus()
        .isForbidden
    }
  }
}
