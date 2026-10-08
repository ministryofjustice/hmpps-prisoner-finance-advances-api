package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.exceptions.RetryAfterConflictException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.SubAccountResponse
import java.time.Instant
import java.util.UUID
import kotlin.collections.emptyList

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
    fun `should create sub account when sub account does not exist for prisoner`() {
      val offenderId = "A1234BC"

      val parentId = UUID.randomUUID()
      val subId = UUID.randomUUID()

      val parent = AccountResponse(
        id = parentId,
        reference = offenderId,
        createdBy = "test-user",
        createdAt = Instant.now(),
        subAccounts = emptyList(),
        type = AccountResponse.Type.PRISONER,
      )

      val createdSub = SubAccountResponse(
        id = subId,
        reference = "CASH",
        parentAccountId = parentId,
        createdBy = "test-user",
        createdAt = Instant.now(),
      )

      whenever(apiClient.createSubAccount(parentId, "CASH")).thenReturn(createdSub)

      val cache = InMemoryAccountCache().apply {
        put(offenderId, parent)
      }

      val result = accountResolver.resolveSubAccount(offenderId, "CASH", cache, true)

      assertEquals(subId, result)
      verify(apiClient).createSubAccount(parentId, "CASH")
    }

    @Test
    fun `should find existing parent account when not present in cache`() {
      val offenderId = "A1234BC"

      val parentId = UUID.randomUUID()

      val parent = AccountResponse(
        id = parentId,
        reference = offenderId,
        createdBy = "test-user",
        createdAt = Instant.now(),
        subAccounts = emptyList(),
        type = AccountResponse.Type.PRISONER,
      )

      whenever(apiClient.findAccountByReference(offenderId)).thenReturn(parent)
      whenever(apiClient.createSubAccount(parentId, "CASH"))
        .thenReturn(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "CASH",
            parentAccountId = parentId,
            createdBy = "test-user",
            createdAt = Instant.now(),
          ),
        )

      val cache = InMemoryAccountCache()

      accountResolver.resolveSubAccount(offenderId, "CASH", cache, true)

      verify(apiClient).findAccountByReference(offenderId)
      verify(apiClient, never()).createAccount(any(), any())
    }

    @Test
    fun `should create parent account and sub account when not found`() {
      val offenderId = "A1234BC"

      val parentId = UUID.randomUUID()

      whenever(apiClient.findAccountByReference(offenderId)).thenReturn(null)
      whenever(apiClient.createAccount(offenderId, CreateAccountRequest.Type.PRISONER))
        .thenReturn(
          AccountResponse(
            id = parentId,
            reference = offenderId,
            createdBy = "test-user",
            createdAt = Instant.now(),
            subAccounts = emptyList(),
            type = AccountResponse.Type.PRISONER,
          ),
        )

      whenever(apiClient.createSubAccount(eq(parentId), any()))
        .thenReturn(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "CASH",
            parentAccountId = parentId,
            createdBy = "test-user",
            createdAt = Instant.now(),
          ),
        )

      val cache = InMemoryAccountCache()

      accountResolver.resolveSubAccount(offenderId, "CASH", cache, true)

      verify(apiClient).findAccountByReference(offenderId)
      verify(apiClient).createAccount(offenderId, CreateAccountRequest.Type.PRISONER)
    }

    @Test
    fun `should try to get parent account again when create account throws exception 409`() {
      val prisonId = "MDI"
      val offenderId = "A1234BC"

      val parentId = UUID.randomUUID()

      whenever(apiClient.findAccountByReference(prisonId))
        .thenReturn(null)
        .thenReturn(
          AccountResponse(
            id = parentId,
            reference = offenderId,
            createdBy = "test-user",
            createdAt = Instant.now(),
            subAccounts = emptyList(),
            type = AccountResponse.Type.PRISON,
          ),
        )

      whenever(apiClient.createAccount(prisonId, CreateAccountRequest.Type.PRISON))
        .thenThrow(WebClientResponseException(409, "Duplicate account reference: $offenderId", null, null, null))

      whenever(apiClient.createSubAccount(eq(parentId), any()))
        .thenReturn(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "1101:CANT",
            parentAccountId = parentId,
            createdBy = "test-user",
            createdAt = Instant.now(),
          ),
        )

      val cache = InMemoryAccountCache()

      accountResolver.resolveSubAccount(prisonId, "1101:CANT", cache, false)

      verify(apiClient, times(2)).findAccountByReference(prisonId)
      verify(apiClient).createAccount(prisonId, CreateAccountRequest.Type.PRISON)
    }

    @Test
    fun `should try to get sub account again when create account throws exception 409`() {
      val prisonId = "MDI"
      val offenderId = "A1234BC"
      val entryCode = 2101
      val transactionType = "CANT"
      val subAccountReference = "CASH"

      val parentId = UUID.randomUUID()

      whenever(apiClient.findAccountByReference(offenderId))
        .thenReturn(
          AccountResponse(
            id = parentId,
            reference = offenderId,
            createdBy = "test-user",
            createdAt = Instant.now(),
            subAccounts = emptyList(),
            type = AccountResponse.Type.PRISONER,
          ),
        )

      whenever(apiClient.createSubAccount(eq(parentId), any()))
        .thenThrow(WebClientResponseException(409, "Duplicate account reference: $offenderId", null, null, null))

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

      accountResolver.resolveSubAccount(offenderId, "CASH", cache, true)

      verify(apiClient).findAccountByReference(offenderId)
      verify(apiClient).findSubAccount(offenderId, subAccountReference)
      verify(apiClient).createSubAccount(parentId, subAccountReference)
    }

    @Test
    fun `should throw RetryAfterConflictException when second get fails after conflict 409 on parent account`() {
      val prisonId = "MDI"

      whenever(apiClient.findAccountByReference(prisonId))
        .thenReturn(null)
        .thenReturn(null)

      whenever(apiClient.createAccount(prisonId, CreateAccountRequest.Type.PRISON))
        .thenThrow(WebClientResponseException(409, "Duplicate account reference: $prisonId", null, null, null))

      val cache = InMemoryAccountCache()

      assertThrows<RetryAfterConflictException> {
        accountResolver.resolveSubAccount(prisonId, "1101:CANT", cache, false)
      }

      verify(apiClient, times(2)).findAccountByReference(prisonId)
      verify(apiClient).createAccount(prisonId, CreateAccountRequest.Type.PRISON)
    }

    @Test
    fun `should throw RetryAfterConflictException when get fails after conflict 409 on sub account`() {
      val prisonId = "MDI"
      val offenderId = "A1234BC"
      val entryCode = 2101
      val transactionType = "CANT"
      val subAccountReference = "CASH"

      val parentId = UUID.randomUUID()

      whenever(apiClient.findAccountByReference(offenderId))
        .thenReturn(
          AccountResponse(
            id = parentId,
            reference = offenderId,
            createdBy = "test-user",
            createdAt = Instant.now(),
            subAccounts = emptyList(),
            type = AccountResponse.Type.PRISONER,
          ),
        )

      whenever(apiClient.createSubAccount(eq(parentId), any()))
        .thenThrow(WebClientResponseException(409, "Duplicate account reference: $offenderId", null, null, null))

      whenever(apiClient.findSubAccount(offenderId, subAccountReference))
        .thenReturn(null)

      val cache = InMemoryAccountCache()

      assertThrows<RetryAfterConflictException> {
        accountResolver.resolveSubAccount(offenderId, "CASH", cache, true)
      }

      verify(apiClient).findAccountByReference(offenderId)
      verify(apiClient).findSubAccount(offenderId, subAccountReference)
      verify(apiClient).createSubAccount(parentId, subAccountReference)
    }

    @Test
    fun `should propagate NOT (409) Conflict HTTP error when POST parent account`() {
      val prisonId = "MDI"

      whenever(apiClient.findAccountByReference(prisonId))
        .thenReturn(null)

      whenever(apiClient.createAccount(prisonId, CreateAccountRequest.Type.PRISON))
        .thenThrow(WebClientResponseException(500, "Server Error", null, null, null))

      val cache = InMemoryAccountCache()

      assertThrows<WebClientResponseException> {
        accountResolver.resolveSubAccount(prisonId, "1101:CANT", cache, false)
      }

      verify(apiClient).findAccountByReference(prisonId)
      verify(apiClient).createAccount(prisonId, CreateAccountRequest.Type.PRISON)
    }

    @Test
    fun `should propagate NOT (409) Conflict HTTP error Exception when POST sub account`() {
      val prisonId = "MDI"
      val offenderId = "A1234BC"
      val entryCode = 2101
      val transactionType = "CANT"
      val subAccountReference = "CASH"

      val parentId = UUID.randomUUID()

      whenever(apiClient.findAccountByReference(offenderId))
        .thenReturn(
          AccountResponse(
            id = parentId,
            reference = offenderId,
            createdBy = "test-user",
            createdAt = Instant.now(),
            subAccounts = emptyList(),
            type = AccountResponse.Type.PRISONER,
          ),
        )

      whenever(apiClient.createSubAccount(eq(parentId), any()))
        .thenThrow(WebClientResponseException(500, "Server Error", null, null, null))

      val cache = InMemoryAccountCache()

      assertThrows<WebClientResponseException> {
        accountResolver.resolveSubAccount(offenderId, "CASH", cache, true)
      }

      verify(apiClient).findAccountByReference(offenderId)
      verify(apiClient, never()).findSubAccount(offenderId, subAccountReference)
      verify(apiClient).createSubAccount(parentId, subAccountReference)
    }
  }
}
