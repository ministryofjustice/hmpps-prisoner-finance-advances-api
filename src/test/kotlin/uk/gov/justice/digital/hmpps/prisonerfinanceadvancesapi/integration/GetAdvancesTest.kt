package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.config.ROLE_PRISONER_FINANCE__ADVANCES__RW


class GetAdvancesTest : IntegrationTestBase() {

  @Test
  fun `should get all advances for prisonNumber`() {

    val prisonNumber = "1234AB"
    val response = webTestClient.get().uri("/advances/$prisonNumber")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__ADVANCES__RW)))
      .exchange()
      .expectStatus()
      .isCreated
      .expectBody<Page<AdvanceRecordResponse>>()
      .returnResult()
      .responseBody!!

    assertThat(response.content)
  }
}