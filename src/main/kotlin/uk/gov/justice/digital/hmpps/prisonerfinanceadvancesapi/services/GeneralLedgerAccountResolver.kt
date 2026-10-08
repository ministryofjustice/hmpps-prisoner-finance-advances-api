package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.clients.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.exceptions.RetryAfterConflictException
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.SubAccountResponse
import java.util.UUID

@Service
class GeneralLedgerAccountResolver(
  private val apiClient: GeneralLedgerApiClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(GeneralLedgerAccountResolver::class.java)
  }

  fun resolveSubAccount(parentRef: String, subRef: String, parentCache: InMemoryAccountCache, isPrisoner: Boolean): UUID = getOrCreateSubAccount(parentRef, subRef, parentCache, isPrisoner = isPrisoner)

  private fun findOrCreateParent(reference: String, isPrisoner: Boolean): AccountResponse {
    val response = apiClient.findAccountByReference(reference)
    if (response != null) {
      return response
    } else {
      log.info("General Ledger account not found for '$reference'. Creating new account.")

      try {
        return apiClient.createAccount(reference, if (isPrisoner) CreateAccountRequest.Type.PRISONER else CreateAccountRequest.Type.PRISON)
      } catch (e: WebClientResponseException) {
        if (e.statusCode == HttpStatus.CONFLICT) {
          return apiClient.findAccountByReference(reference)
            ?: throw RetryAfterConflictException("Account not found after server responded with 409 for reference: $reference")
        } else {
          throw e
        }
      }
    }
  }

  private fun getOrCreateSubAccount(
    parentRef: String,
    subRef: String,
    parentCache: InMemoryAccountCache,
    isPrisoner: Boolean,
  ): UUID {
    val parent = parentCache.getOrPut(parentRef) {
      findOrCreateParent(parentRef, isPrisoner)
    }

    parent.subAccounts
      .firstOrNull { it.reference == subRef }
      ?.let { return it.id }

    val created = createSubAccount(parent.id, subRef, parentRef)

    // We need to create a new accountResponse to update the sub accounts
    val updatedParent = parent.copy(
      subAccounts = parent.subAccounts + created,
    )

    parentCache.put(parentRef, updatedParent)

    return created.id
  }

  private fun createSubAccount(parentAccountId: UUID, reference: String, parentReference: String): SubAccountResponse {
    log.info("General Ledger sub-account not found for '$reference'. Creating new sub-account.")
    try {
      return apiClient.createSubAccount(parentAccountId, reference)
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.CONFLICT) {
        return apiClient.findSubAccount(parentReference, reference)
          ?: throw RetryAfterConflictException("Sub account not found after server responded with 409 for reference: $reference")
      } else {
        throw e
      }
    }
  }
}
