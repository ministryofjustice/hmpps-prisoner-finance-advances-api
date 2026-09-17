package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.enums.AdvanceStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.responses.PagedResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

class GetAdvancesTest : IntegrationTestBase() {

  @BeforeEach
  fun setUp() {
    this.integrationTestHelpers.clearDB()
  }

  @Test
  fun `should get all advances for prisonNumber`() {
    val prisonNumber = "X1234AB"

    repeat(10) { i ->
      this.integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
        legacyPaymentProfileId = i.toString(),
        legacyInformationNumber = i.toString(),
        amount = i.toLong(),
        prisonId = "LEI",
        repaymentAmount = 5,
        status = AdvanceStatus.ACTIVE,
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
    assertThat(firstAdvance.prisonID).isEqualTo("LEI")
    assertThat(firstAdvance.legacyPaymentProfileId).isEqualTo("9")
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
    val prisonNumber = "C1234AB"

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
    val prisonNumber = "A1345BC"

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
    val prisonNumber = "A12345BC"

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
    val prisonNumber = "A123 45BC"

    webTestClient.get().uri("/advances/$prisonNumber")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  @Test
  fun `should return 400 BAD REQUEST when requesting a page that doesnt exist`() {
    val prisonNumber = "A1245BC"

    repeat(25) { i ->
      this.integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
        legacyPaymentProfileId = i.toString(),
        legacyInformationNumber = i.toString(),
        amount = i.toLong(),
        prisonId = "LEI",
        repaymentAmount = 5,
        status = AdvanceStatus.ACTIVE,
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
    val prisonNumber = "A1245BC"

    repeat(25) { i ->
      this.integrationTestHelpers.createAdvance(
        prisonNumber = prisonNumber,
        legacyPaymentProfileId = i.toString(),
        legacyInformationNumber = i.toString(),
        amount = i.toLong(),
        prisonId = "LEI",
        repaymentAmount = 5,
        status = AdvanceStatus.ACTIVE,
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
    val prisonNumber = "A9971EC"

    webTestClient.get().uri("/advances/$prisonNumber")
      .headers(setAuthorisation(roles = listOf("INVALID_ROLE")))
      .exchange()
      .expectStatus()
      .isForbidden
  }
}
