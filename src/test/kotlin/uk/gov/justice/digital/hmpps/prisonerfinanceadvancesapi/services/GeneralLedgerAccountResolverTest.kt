package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.exceptions.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.SubAccountResponse
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GeneralLedgerAccountResolverTest {

  @Mock
  private lateinit var apiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var accountResolver: GeneralLedgerAccountResolver

  @Nested
  @DisplayName("resolvePrisonerSubAccount")
  inner class ResolveSubAccount {

    @Test
    fun `should try to get sub account`() {
      val offenderId = "A1234BC"
      val subAccountReference = "CASH"

      val parentId = UUID.randomUUID()

      whenever(apiClient.findSubAccount(offenderId, subAccountReference))
        .thenReturn(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = subAccountReference,
            parentAccountId = parentId,
            createdBy = "test-user",
            createdAt = Instant.now(),
          ),
        )

      val cache = InMemoryAccountCache()

      accountResolver.resolveSubAccount(offenderId, "CASH", cache)

      verify(apiClient).findSubAccount(offenderId, subAccountReference)
    }

    @Test
    fun `should try to get sub account and return a Custom Exception when GL returns null`() {
      val offenderId = "A1234BC"
      val subAccountReference = "CASH"

      whenever(apiClient.findSubAccount(offenderId, subAccountReference))
        .thenReturn(null)

      val cache = InMemoryAccountCache()

      val exception = assertThrows<CustomException> {
        accountResolver.resolveSubAccount(offenderId, "CASH", cache)
      }

      assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND)

      verify(apiClient).findSubAccount(offenderId, subAccountReference)
    }
  }
}
