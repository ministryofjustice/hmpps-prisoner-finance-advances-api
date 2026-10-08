package uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.services

import uk.gov.justice.digital.hmpps.prisonerfinanceadvancesapi.models.generalledger.AccountResponse

interface AccountCache {
  fun put(parentRef: String, account: AccountResponse)
  fun getOrPut(
    parentRef: String,
    supplier: () -> AccountResponse,
  ): AccountResponse
  fun clear()
}
